package com.focusforge.identity;

import com.focusforge.identity.config.RpgClock;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RpgClock clock;

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> register(String email, String password, String name) {
        return http.postForEntity("/api/auth/register",
                Map.of("email", email, "password", password, "displayName", name), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> me(String userId) {
        HttpHeaders h = new HttpHeaders();
        if (userId != null) {
            h.set(GatewayHeaders.USER_ID, userId);
        }
        return http.exchange("/api/profile/me", HttpMethod.GET, new HttpEntity<>(h), Map.class);
    }

    private static String uniqueEmail() {
        return "player-" + UUID.randomUUID() + "@focus.dev";
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerIssuesTokenAndCreatesFreshProfile() {
        var res = register(uniqueEmail(), "correct-horse", "Ada");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) res.getBody().get("accessToken")).contains(".");
        var user = (Map<String, Object>) res.getBody().get("user");
        assertThat(user.get("role")).isEqualTo("PLAYER");

        var profile = me((String) user.get("id")).getBody();
        assertThat(profile).containsEntry("level", 1)
                .containsEntry("hp", 100)
                .containsEntry("maxHp", 100)
                .containsEntry("xp", 0)
                .containsEntry("xpForNextLevel", 100)
                .containsEntry("displayName", "Ada");
    }

    @Test
    void emailsAreCaseInsensitiveAndUnique() {
        String email = uniqueEmail();
        assertThat(register(email, "password-1", "One").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var dup = register(email.toUpperCase(), "password-2", "Two");
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getHeaders().getContentType().toString()).contains("problem+json");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void loginAcceptsCorrectPasswordOnly() {
        String email = uniqueEmail();
        register(email, "s3cret-pass", "Lin");

        ResponseEntity<Map> ok = http.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "s3cret-pass"), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> bad = http.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "wrong-pass"), Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> unknown = http.postForEntity("/api/auth/login",
                Map.of("email", uniqueEmail(), "password", "whatever1"), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getBody().get("detail")).isEqualTo(bad.getBody().get("detail"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuredAdminEmailGetsAdminRole() {
        var res = register("Boss@Focus.dev", "admin-pass-1", "Boss");
        var user = (Map<String, Object>) res.getBody().get("user");
        assertThat(user.get("role")).isEqualTo("ADMIN");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validationErrorsListOffendingFields() {
        var res = register("not-an-email", "short", "");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var errors = (Map<String, String>) res.getBody().get("errors");
        assertThat(errors).containsKeys("email", "password", "displayName");
    }

    @Test
    void profileRequiresGatewayUserHeader() {
        assertThat(me(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me("not-a-uuid").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void firstRequestOfANewDayRefillsHp() {
        var user = (Map<String, Object>) register(uniqueEmail(), "password-x", "Tired").getBody().get("user");
        String id = (String) user.get("id");

        // Simulate: player drained HP yesterday.
        jdbc.update("UPDATE player_profiles SET hp = 15, hp_reset_on = ? WHERE user_id = ?::uuid",
                clock.today().minusDays(1), id);

        assertThat(me(id).getBody()).containsEntry("hp", 100);
        assertThat(jdbc.queryForObject("SELECT hp FROM player_profiles WHERE user_id = ?::uuid",
                Integer.class, id)).isEqualTo(100);
    }
}
