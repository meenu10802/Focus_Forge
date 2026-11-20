package com.focusforge.quest;

import com.focusforge.events.Topics;
import com.focusforge.quest.ai.AiQuestClient;
import com.focusforge.quest.ai.AiQuestClient.Plan;
import com.focusforge.quest.ai.AiQuestClient.PlannedTask;
import com.focusforge.quest.quest.Difficulty;
import com.focusforge.web.GatewayHeaders;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SuppressWarnings({"rawtypes", "unchecked"})
class FocusFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;

    /** Every record seen on our topics during the test class, collected in the background. */
    static final List<ConsumerRecord<String, String>> SEEN = new CopyOnWriteArrayList<>();
    static KafkaConsumer<String, String> consumer;
    static Thread poller;
    static volatile boolean polling = true;

    @BeforeAll
    static void startConsumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // This consumer subscribes before the app creates the 3-partition topics; refresh metadata
        // quickly so it notices the extra partitions (default refresh is 5 minutes).
        p.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 500);
        consumer = new KafkaConsumer<>(p);
        consumer.subscribe(List.of(Topics.TASK_COMPLETED, Topics.TASK_ABANDONED));
        poller = new Thread(() -> {
            while (polling) {
                consumer.poll(Duration.ofMillis(200)).forEach(SEEN::add);
            }
            consumer.close();
        });
        poller.start();
    }

    @AfterAll
    static void stopConsumer() throws InterruptedException {
        polling = false;
        poller.join(5000);
    }

    // --- helpers -----------------------------------------------------------

    private static HttpHeaders as(UUID user) {
        HttpHeaders h = new HttpHeaders();
        h.set(GatewayHeaders.USER_ID, user.toString());
        return h;
    }

    private static HttpHeaders as(UUID user, String idemKey) {
        HttpHeaders h = as(user);
        h.set("Idempotency-Key", idemKey);
        return h;
    }

    /** Creates a quest with the given difficulties and returns its task ids in order. */
    private List<String> questFor(UUID user, Difficulty... difficulties) {
        List<PlannedTask> tasks = new ArrayList<>();
        for (Difficulty d : difficulties) {
            tasks.add(new PlannedTask("Task " + d, "", 10, d));
        }
        given(ai.questline(anyString())).willReturn(new Plan("Q", AiQuestClient.Source.AI_GENERATED, "test", tasks));
        Map body = http.exchange("/api/quests/generate", HttpMethod.POST,
                new HttpEntity<>(Map.of("goal", "Learn things"), as(user)), Map.class).getBody();
        return ((List<Map<String, Object>>) body.get("tasks")).stream().map(t -> (String) t.get("id")).toList();
    }

    private ResponseEntity<Map> post(UUID user, String path) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(as(user)), Map.class);
    }

    private ResponseEntity<Map> complete(UUID user, String taskId, String key) {
        return http.exchange("/api/quests/tasks/" + taskId + "/complete", HttpMethod.POST,
                new HttpEntity<>(as(user, key)), Map.class);
    }

    private int outboxRows(String topic, String taskId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE topic = ? AND payload LIKE ?",
                Integer.class, topic, "%" + taskId + "%");
    }

    private List<ConsumerRecord<String, String>> awaitKafka(String topic, String taskId, int atLeast)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        List<ConsumerRecord<String, String>> hits;
        do {
            hits = SEEN.stream().filter(r -> r.topic().equals(topic) && r.value().contains(taskId)).toList();
            if (hits.size() >= atLeast) {
                Thread.sleep(1000); // give any (wrong) duplicate time to show up too
                return SEEN.stream().filter(r -> r.topic().equals(topic) && r.value().contains(taskId)).toList();
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return hits;
    }

    // --- tests -------------------------------------------------------------

    @Test
    void sameIdempotencyKeyReplaysResponseAndEmitsOneEvent() throws Exception {
        UUID user = UUID.randomUUID();
        String task = questFor(user, Difficulty.MEDIUM, Difficulty.EASY).get(0);
        String key = UUID.randomUUID().toString();

        var first = complete(user, task, key);
        var second = complete(user, task, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).containsEntry("coinsEarned", 20).containsEntry("xpEarned", 50)
                .containsEntry("hpRestored", 10).containsEntry("questCompleted", false);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getBody()).isEqualTo(first.getBody());

        assertThat(outboxRows(Topics.TASK_COMPLETED, task)).isEqualTo(1);
        var records = awaitKafka(Topics.TASK_COMPLETED, task, 1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo(user.toString());
        assertThat(records.get(0).value()).contains("\"coinsEarned\":20");
    }

    @Test
    void differentKeyForAlreadyCompletedTaskIsRejected() {
        UUID user = UUID.randomUUID();
        String task = questFor(user, Difficulty.EASY, Difficulty.EASY).get(0);

        assertThat(complete(user, task, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(complete(user, task, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(outboxRows(Topics.TASK_COMPLETED, task)).isEqualTo(1);
    }

    @Test
    void concurrentCompletionsWithDifferentKeysCreditExactlyOnce() throws Exception {
        UUID user = UUID.randomUUID();
        String task = questFor(user, Difficulty.HARD, Difficulty.EASY).get(0);
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpStatus>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return (HttpStatus) complete(user, task, UUID.randomUUID().toString()).getStatusCode();
            }));
        }
        go.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (var f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == HttpStatus.OK).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == HttpStatus.CONFLICT).hasSize(n - 1);
        assertThat(outboxRows(Topics.TASK_COMPLETED, task)).isEqualTo(1);
    }

    @Test
    void reusingAKeyForAnotherTaskIs422() {
        UUID user = UUID.randomUUID();
        List<String> tasks = questFor(user, Difficulty.EASY, Difficulty.EASY);
        String key = UUID.randomUUID().toString();

        assertThat(complete(user, tasks.get(0), key).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(complete(user, tasks.get(1), key).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void keysAreScopedPerPlayer() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String aliceTask = questFor(alice, Difficulty.EASY, Difficulty.EASY).get(0);
        String bobTask = questFor(bob, Difficulty.EASY, Difficulty.EASY).get(0);
        String sharedKey = "same-key-" + UUID.randomUUID();

        assertThat(complete(alice, aliceTask, sharedKey).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(complete(bob, bobTask, sharedKey).getStatusCode()).isEqualTo(HttpStatus.OK);
        // and nobody can complete someone else's task
        assertThat(complete(bob, aliceTask, UUID.randomUUID().toString()).getStatusCode())
                .isIn(HttpStatus.NOT_FOUND);
    }

    @Test
    void idempotencyKeyIsRequiredAndValidated() {
        UUID user = UUID.randomUUID();
        String task = questFor(user, Difficulty.EASY, Difficulty.EASY).get(0);

        assertThat(post(user, "/api/quests/tasks/" + task + "/complete").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(complete(user, task, "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(complete(user, task, "has spaces in it!").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void focusSessionLifecycleAndAbandonEvent() throws Exception {
        UUID user = UUID.randomUUID();
        List<String> tasks = questFor(user, Difficulty.EASY, Difficulty.MEDIUM);

        var started = post(user, "/api/quests/tasks/" + tasks.get(0) + "/start");
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody()).containsEntry("status", "ACTIVE").containsEntry("plannedMinutes", 10);

        // starting the same task again is idempotent; a different one is blocked
        assertThat(post(user, "/api/quests/tasks/" + tasks.get(0) + "/start").getBody().get("sessionId"))
                .isEqualTo(started.getBody().get("sessionId"));
        assertThat(post(user, "/api/quests/tasks/" + tasks.get(1) + "/start").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        var active = http.exchange("/api/quests/sessions/active", HttpMethod.GET, new HttpEntity<>(as(user)), Map.class);
        assertThat(active.getBody()).containsEntry("taskId", tasks.get(0));

        var abandoned = post(user, "/api/quests/tasks/" + tasks.get(0) + "/abandon");
        assertThat(abandoned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(abandoned.getBody()).containsEntry("hpLost", 10);
        assertThat(post(user, "/api/quests/tasks/" + tasks.get(0) + "/abandon").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(http.exchange("/api/quests/sessions/active", HttpMethod.GET, new HttpEntity<>(as(user)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(awaitKafka(Topics.TASK_ABANDONED, tasks.get(0), 1)).hasSize(1);

        // an abandoned task can be picked up again
        assertThat(post(user, "/api/quests/tasks/" + tasks.get(0) + "/start").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void completingTheLastTaskCompletesTheQuestAndRecordsFocusTime() {
        UUID user = UUID.randomUUID();
        List<String> tasks = questFor(user, Difficulty.EASY, Difficulty.EASY);

        post(user, "/api/quests/tasks/" + tasks.get(0) + "/start");
        var first = complete(user, tasks.get(0), UUID.randomUUID().toString());
        assertThat(first.getBody()).containsEntry("questCompleted", false).containsEntry("focusMinutes", 1);

        var last = complete(user, tasks.get(1), UUID.randomUUID().toString()); // checked off without a timer
        assertThat(last.getBody()).containsEntry("questCompleted", true).containsEntry("focusMinutes", 10);

        Integer sessions = jdbc.queryForObject(
                "SELECT count(*) FROM focus_sessions WHERE user_id = ?::uuid AND status = 'COMPLETED'",
                Integer.class, user.toString());
        assertThat(sessions).isEqualTo(2);
    }
}
