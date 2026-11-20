package com.focusforge.identity;

import com.focusforge.events.TaskAbandonedEvent;
import com.focusforge.events.TaskCompletedEvent;
import com.focusforge.events.Topics;
import com.focusforge.web.GatewayHeaders;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings({"rawtypes", "unchecked"})
class ProgressIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;
    @Autowired
    KafkaTemplate<String, Object> kafka;

    private UUID newPlayer() {
        Map body = http.postForEntity("/api/auth/register", Map.of(
                "email", "p-" + UUID.randomUUID() + "@focus.dev", "password", "password-123", "displayName", "Player"),
                Map.class).getBody();
        return UUID.fromString((String) ((Map) body.get("user")).get("id"));
    }

    private Map me(UUID user) {
        HttpHeaders h = new HttpHeaders();
        h.set(GatewayHeaders.USER_ID, user.toString());
        return http.exchange("/api/profile/me", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody();
    }

    private void send(String topic, Object event, UUID user) throws Exception {
        kafka.send(topic, user.toString(), event).get();
    }

    @Test
    void completionAddsXpRestoresHpAndInvalidatesTheCachedDashboard() throws Exception {
        UUID user = newPlayer();
        assertThat(me(user)).containsEntry("hp", 100).containsEntry("xp", 0); // now cached in Redis

        send(Topics.TASK_ABANDONED, TaskAbandonedEvent.of(user, UUID.randomUUID(), 30), user);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(me(user)).containsEntry("hp", 70));

        send(Topics.TASK_COMPLETED, TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(),
                20, 50, 10, 12), user);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(me(user)).containsEntry("hp", 80).containsEntry("xp", 50).containsEntry("level", 1));
    }

    @Test
    void hpIsCappedAtMaxAndFloorsAtZero() throws Exception {
        UUID user = newPlayer();
        send(Topics.TASK_COMPLETED, TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(),
                5, 1, 50, 10), user);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(me(user)).containsEntry("hp", 100).containsEntry("xp", 1));

        send(Topics.TASK_ABANDONED, TaskAbandonedEvent.of(user, UUID.randomUUID(), 500), user);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(me(user)).containsEntry("hp", 0));
    }

    @Test
    void duplicateDeliveryIsAppliedOnce() throws Exception {
        UUID user = newPlayer();
        var event = TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(), 20, 30, 0, 10);
        send(Topics.TASK_COMPLETED, event, user);
        send(Topics.TASK_COMPLETED, event, user);
        send(Topics.TASK_COMPLETED, TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(),
                1, 1, 0, 1), user); // marker behind the duplicate on the same partition

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(me(user)).containsEntry("xp", 31));
    }

    @Test
    void crossingTheThresholdLevelsUpAndPublishesLevelUpEvent() throws Exception {
        UUID user = newPlayer();
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "lvl-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 500);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (var consumer = new KafkaConsumer<String, String>(p)) {
            consumer.subscribe(List.of(Topics.LEVEL_UP));
            // 400 XP from level 1: 100 (->2) + 283 (->3) = 383, leaving 17 into level 3
            send(Topics.TASK_COMPLETED, TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(),
                    35, 400, 15, 15), user);

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(me(user)).containsEntry("level", 3).containsEntry("xp", 17)
                            .containsEntry("xpForNextLevel", 520));

            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (var r : consumer.poll(Duration.ofMillis(300))) {
                    if (user.toString().equals(r.key()) && r.value().contains("\"previousLevel\":1")
                            && r.value().contains("\"newLevel\":3")) {
                        return true;
                    }
                }
                return false;
            });
        }
    }
}
