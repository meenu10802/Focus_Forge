package com.focusforge.quest;

import com.focusforge.quest.ai.AiQuestClient;
import com.focusforge.quest.ai.AiQuestClient.Plan;
import com.focusforge.quest.ai.AiQuestClient.PlannedTask;
import com.focusforge.quest.quest.Difficulty;
import com.focusforge.web.ApiException;
import com.focusforge.web.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

class QuestApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    private static HttpHeaders as(UUID userId) {
        HttpHeaders h = new HttpHeaders();
        h.set(GatewayHeaders.USER_ID, userId.toString());
        return h;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> generate(UUID user, String goal) {
        return http.exchange("/api/quests/generate", HttpMethod.POST,
                new HttpEntity<>(Map.of("goal", goal), as(user)), Map.class);
    }

    private static Plan plan(Difficulty... difficulties) {
        var tasks = new java.util.ArrayList<PlannedTask>();
        for (int i = 0; i < difficulties.length; i++) {
            tasks.add(new PlannedTask("Task " + (i + 1), "desc", 12, difficulties[i]));
        }
        return new Plan("Master JWT", AiQuestClient.Source.AI_GENERATED, "groq", tasks);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatePersistsQuestWithServerSideRewards() {
        given(ai.questline("Learn JWT in 3 days")).willReturn(plan(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD));
        UUID user = UUID.randomUUID();

        var res = generate(user, "  Learn JWT in 3 days ");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = res.getBody();
        assertThat(body).containsEntry("title", "Master JWT")
                .containsEntry("source", "AI_GENERATED")
                .containsEntry("status", "ACTIVE")
                .containsEntry("totalCoins", 10 + 20 + 35)
                .containsEntry("totalXp", 25 + 50 + 80)
                .containsEntry("totalMinutes", 36);
        var tasks = (List<Map<String, Object>>) body.get("tasks");
        assertThat(tasks).extracting(t -> t.get("position")).containsExactly(1, 2, 3);
        assertThat(tasks.get(1)).containsEntry("coins", 20).containsEntry("xp", 50)
                .containsEntry("hpRestore", 10).containsEntry("status", "PENDING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAndGetAreScopedToTheOwner() {
        given(ai.questline(anyString())).willReturn(plan(Difficulty.EASY, Difficulty.EASY, Difficulty.EASY));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String first = (String) generate(alice, "Learn Kafka").getBody().get("id");
        String second = (String) generate(alice, "Learn Redis").getBody().get("id");

        ResponseEntity<List> aliceList = http.exchange("/api/quests", HttpMethod.GET, new HttpEntity<>(as(alice)), List.class);
        assertThat(((List<Map<String, Object>>) aliceList.getBody()))
                .extracting(q -> q.get("id")).containsExactly(second, first); // newest first

        ResponseEntity<List> bobList = http.exchange("/api/quests", HttpMethod.GET, new HttpEntity<>(as(bob)), List.class);
        assertThat(bobList.getBody()).isEmpty();

        assertThat(http.exchange("/api/quests/" + first, HttpMethod.GET, new HttpEntity<>(as(alice)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/quests/" + first, HttpMethod.GET, new HttpEntity<>(as(bob)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fallbackPlansAreStoredAndLabelled() {
        given(ai.questline(anyString())).willReturn(new Plan("Quest: x", AiQuestClient.Source.FALLBACK, "template",
                List.of(new PlannedTask("Map the territory", "", 10, Difficulty.EASY))));

        var body = generate(UUID.randomUUID(), "Learn Go").getBody();

        assertThat(body).containsEntry("source", "FALLBACK").containsEntry("provider", "template");
    }

    @Test
    void aiServiceOutageIs503ProblemAndNothingIsSaved() {
        given(ai.questline(anyString())).willThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Quest Generator Unavailable", "offline"));
        UUID user = UUID.randomUUID();

        var res = generate(user, "Learn Rust");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(http.exchange("/api/quests", HttpMethod.GET, new HttpEntity<>(as(user)), List.class).getBody())
                .isEmpty();
    }

    @Test
    void goalIsValidated() {
        assertThat(generate(UUID.randomUUID(), "x").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(generate(UUID.randomUUID(), "y".repeat(301)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requiresGatewayIdentity() {
        assertThat(http.getForEntity("/api/quests", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
