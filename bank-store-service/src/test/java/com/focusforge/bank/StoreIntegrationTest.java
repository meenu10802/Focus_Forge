package com.focusforge.bank;

import com.focusforge.web.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings({"rawtypes", "unchecked"})
class StoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;

    // --- helpers -----------------------------------------------------------

    private static HttpHeaders as(UUID user) {
        HttpHeaders h = new HttpHeaders();
        h.set(GatewayHeaders.USER_ID, user.toString());
        return h;
    }

    private static HttpHeaders admin() {
        HttpHeaders h = as(UUID.randomUUID());
        h.set(GatewayHeaders.USER_ROLES, "ADMIN");
        return h;
    }

    /** Fund a wallet directly (in production coins only arrive via TaskCompletedEvent). */
    private void fund(UUID user, long coins) {
        jdbc.update("INSERT INTO wallets (user_id, balance) VALUES (?, ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET balance = EXCLUDED.balance", user, coins);
    }

    private long balance(UUID user) {
        Map w = http.exchange("/api/wallet/me", HttpMethod.GET, new HttpEntity<>(as(user)), Map.class).getBody();
        return ((Number) w.get("balance")).longValue();
    }

    private int stock(String rewardId) {
        return jdbc.queryForObject("SELECT stock FROM rewards WHERE id = ?::uuid", Integer.class, rewardId);
    }

    private List<Map<String, Object>> catalogue(UUID user) {
        return http.exchange("/api/store/rewards", HttpMethod.GET, new HttpEntity<>(as(user)), List.class).getBody();
    }

    private String rewardIdByName(UUID user, String name) {
        return (String) catalogue(user).stream().filter(r -> name.equals(r.get("name"))).findFirst().orElseThrow()
                .get("id");
    }

    private ResponseEntity<Map> redeem(UUID user, String rewardId, String key) {
        HttpHeaders h = as(user);
        h.set("Idempotency-Key", key);
        return http.exchange("/api/store/rewards/" + rewardId + "/redeem", HttpMethod.POST, new HttpEntity<>(h),
                Map.class);
    }

    private String createGlobal(String name, int price, int stock) {
        Map body = Map.of("name", name, "description", "test", "emoji", "🧪", "price", price, "stock", stock);
        return (String) http.exchange("/api/admin/rewards", HttpMethod.POST, new HttpEntity<>(body, admin()), Map.class)
                .getBody().get("id");
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    // --- catalogue ---------------------------------------------------------

    @Test
    void seededGlobalRewardsAreVisibleCheapestFirst() {
        var names = catalogue(UUID.randomUUID()).stream().map(r -> r.get("name")).toList();
        assertThat(names).contains("30 mins Gaming", "Order Biryani");
        var prices = catalogue(UUID.randomUUID()).stream().map(r -> ((Number) r.get("price")).intValue()).toList();
        assertThat(prices).isSorted();
    }

    @Test
    void customRewardsArePrivateToTheirOwner() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        var created = http.exchange("/api/store/rewards", HttpMethod.POST, new HttpEntity<>(
                Map.of("name", "Bubble tea", "price", 70, "stock", 3, "emoji", "🧋"), as(alice)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("mine", true).containsEntry("global", false);
        String id = (String) created.getBody().get("id");

        assertThat(catalogue(alice)).extracting(r -> r.get("id")).contains(id);
        assertThat(catalogue(bob)).extracting(r -> r.get("id")).doesNotContain(id);

        // Bob can't see, edit, delete or redeem it
        fund(bob, 1000);
        assertThat(redeem(bob, id, key()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/store/rewards/" + id, HttpMethod.PUT, new HttpEntity<>(
                Map.of("name", "Hacked", "price", 1, "stock", 999), as(bob)), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/store/rewards/" + id, HttpMethod.DELETE, new HttpEntity<>(as(bob)), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Alice can edit and soft-delete it
        var updated = http.exchange("/api/store/rewards/" + id, HttpMethod.PUT, new HttpEntity<>(
                Map.of("name", "Large bubble tea", "price", 90, "stock", 5), as(alice)), Map.class);
        assertThat(updated.getBody()).containsEntry("name", "Large bubble tea").containsEntry("price", 90);
        assertThat(http.exchange("/api/store/rewards/" + id, HttpMethod.DELETE, new HttpEntity<>(as(alice)), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(catalogue(alice)).extracting(r -> r.get("id")).doesNotContain(id);
    }

    @Test
    void customRewardInputIsValidated() {
        var res = http.exchange("/api/store/rewards", HttpMethod.POST, new HttpEntity<>(
                Map.of("name", "", "price", 0, "stock", -1), as(UUID.randomUUID())), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map<String, String>) res.getBody().get("errors")).containsKeys("name", "price", "stock");
    }

    @Test
    void adminEndpointsRequireAdminRole() {
        var res = http.exchange("/api/admin/rewards", HttpMethod.POST, new HttpEntity<>(
                Map.of("name", "Free money", "price", 1, "stock", 1), as(UUID.randomUUID())), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- redemption --------------------------------------------------------

    @Test
    void redeemDebitsWalletDecrementsStockAndPublishesEvent() {
        UUID user = UUID.randomUUID();
        fund(user, 120);
        String gaming = rewardIdByName(user, "30 mins Gaming");
        int stockBefore = stock(gaming);

        var res = redeem(user, gaming, key());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("rewardName", "30 mins Gaming").containsEntry("price", 50)
                .containsEntry("newBalance", 70);
        assertThat(balance(user)).isEqualTo(70);
        assertThat(stock(gaming)).isEqualTo(stockBefore - 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id = ? AND type = 'DEBIT'",
                Integer.class, user)).isEqualTo(1);

        List history = http.exchange("/api/store/redemptions", HttpMethod.GET, new HttpEntity<>(as(user)), List.class)
                .getBody();
        assertThat(history).hasSize(1);

        // RewardRedeemedEvent leaves through the outbox
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE topic = 'store.reward-redeemed' "
                        + "AND published_at IS NOT NULL AND msg_key = ?", Integer.class, user.toString()) == 1);
    }

    @Test
    void sameIdempotencyKeyReplaysAndChargesOnce() {
        UUID user = UUID.randomUUID();
        fund(user, 500);
        String nap = rewardIdByName(user, "Guilt-free nap");
        String coffee = rewardIdByName(user, "Fancy coffee");
        String k = key();

        var first = redeem(user, nap, k);
        var second = redeem(user, nap, k);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getBody().get("redemptionId")).isEqualTo(first.getBody().get("redemptionId"));
        assertThat(balance(user)).isEqualTo(460);

        assertThat(redeem(user, coffee, k).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balance(user)).isEqualTo(460);
    }

    @Test
    void notEnoughCoinsIsAClean409AndChangesNothing() {
        UUID user = UUID.randomUUID();
        fund(user, 30);
        String biryani = rewardIdByName(user, "Order Biryani");
        int stockBefore = stock(biryani);

        var res = redeem(user, biryani, key());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).containsEntry("title", "Insufficient Coins");
        assertThat((String) res.getBody().get("detail")).contains("270 more coins");
        assertThat(balance(user)).isEqualTo(30);
        assertThat(stock(biryani)).isEqualTo(stockBefore);
    }

    @Test
    void lastUnitRaceExactlyOneWinnerAndStockNeverNegative() throws Exception {
        String lastCheatMeal = createGlobal("Last cheat meal " + UUID.randomUUID(), 10, 1);
        int players = 20;
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            UUID u = UUID.randomUUID();
            fund(u, 100);
            users.add(u);
        }

        ExecutorService pool = Executors.newFixedThreadPool(players);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (UUID u : users) {
            futures.add(pool.submit(() -> {
                go.await();
                return redeem(u, lastCheatMeal, key());
            }));
        }
        go.countDown();
        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (var f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        assertThat(results).filteredOn(r -> r.getStatusCode() == HttpStatus.OK).hasSize(1);
        assertThat(results).filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT).hasSize(players - 1);
        assertThat(results).filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .extracting(r -> r.getBody().get("title"))
                .allMatch(t -> t.equals("Out of Stock") || t.equals("Busy"));
        assertThat(stock(lastCheatMeal)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM redemptions WHERE reward_id = ?::uuid", Integer.class,
                lastCheatMeal)).isEqualTo(1);
        long totalCoins = users.stream().mapToLong(this::balance).sum();
        assertThat(totalCoins).isEqualTo(players * 100L - 10); // exactly one debit happened
    }

    @Test
    void onePlayerCannotDoubleSpendWithParallelRequests() throws Exception {
        UUID user = UUID.randomUUID();
        fund(user, 50);
        String reward = createGlobal("Double-spend bait " + UUID.randomUUID(), 50, 100);
        int n = 10;

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return (HttpStatus) redeem(user, reward, key()).getStatusCode();
            }));
        }
        go.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (var f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == HttpStatus.OK).hasSize(1);
        assertThat(balance(user)).isZero();
        assertThat(stock(reward)).isEqualTo(99);
    }

    @Test
    void redeemRequiresAValidIdempotencyKey() {
        UUID user = UUID.randomUUID();
        String nap = rewardIdByName(user, "Guilt-free nap");
        assertThat(http.exchange("/api/store/rewards/" + nap + "/redeem", HttpMethod.POST, new HttpEntity<>(as(user)),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(redeem(user, nap, "bad key!").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
