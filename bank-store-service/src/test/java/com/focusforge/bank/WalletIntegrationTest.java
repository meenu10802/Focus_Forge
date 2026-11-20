package com.focusforge.bank;

import com.focusforge.bank.wallet.LedgerRepository;
import com.focusforge.events.TaskCompletedEvent;
import com.focusforge.events.Topics;
import com.focusforge.web.GatewayHeaders;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings({"rawtypes", "unchecked"})
class WalletIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    KafkaTemplate<String, Object> kafka;
    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    LedgerRepository ledger;

    private Map wallet(UUID user) {
        HttpHeaders h = new HttpHeaders();
        h.set(GatewayHeaders.USER_ID, user.toString());
        return http.exchange("/api/wallet/me", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody();
    }

    private Map transactions(UUID user) {
        HttpHeaders h = new HttpHeaders();
        h.set(GatewayHeaders.USER_ID, user.toString());
        return http.exchange("/api/wallet/me/transactions", HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody();
    }

    private static Number balanceOf(Map wallet) {
        return (Number) wallet.get("balance");
    }

    private void send(TaskCompletedEvent e) throws Exception {
        kafka.send(Topics.TASK_COMPLETED, e.userId().toString(), e).get();
    }

    @Test
    void newPlayerHasEmptyWallet() {
        assertThat(balanceOf(wallet(UUID.randomUUID())).longValue()).isZero();
    }

    @Test
    void completedTasksAreCreditedAndLedgered() throws Exception {
        UUID user = UUID.randomUUID();
        send(TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(), 20, 50, 10, 12));
        send(TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(), 35, 80, 15, 15));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(balanceOf(wallet(user)).longValue()).isEqualTo(55));

        var page = transactions(user);
        var content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0)).containsEntry("type", "CREDIT").containsEntry("amount", 35)
                .containsEntry("balanceAfter", 55);
        assertThat(ledger.ledgerBalance(user)).isEqualTo(55); // reconciliation invariant
    }

    @Test
    void redeliveredEventIsCreditedOnlyOnce() throws Exception {
        UUID user = UUID.randomUUID();
        var event = TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(), 20, 50, 10, 12);

        send(event);
        send(event); // same eventId: what an outbox relay crash-and-retry looks like
        send(event);
        var marker = TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(), 1, 1, 1, 1);
        send(marker); // same partition (same key) => once this is applied, the duplicates were too

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(balanceOf(wallet(user)).longValue()).isEqualTo(21));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE event_id = ?",
                Integer.class, event.eventId())).isEqualTo(1);
    }

    @Test
    void poisonMessageGoesToDeadLetterTopicAndDoesNotBlockThePartition() throws Exception {
        UUID user = UUID.randomUUID();
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var raw = new KafkaProducer<String, String>(p)) {
            raw.send(new ProducerRecord<>(Topics.TASK_COMPLETED, user.toString(), "{not json at all")).get();
        }
        // a good event on the same key right behind the poison one must still be processed
        send(TaskCompletedEvent.of(user, UUID.randomUUID(), UUID.randomUUID(), 10, 25, 5, 10));
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(balanceOf(wallet(user)).longValue()).isEqualTo(10));

        Properties c = new Properties();
        c.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        c.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID());
        c.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        c.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        c.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (var dlt = new KafkaConsumer<String, byte[]>(c)) {
            dlt.subscribe(List.of(Topics.TASK_COMPLETED + Topics.DLT_SUFFIX));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (var r : dlt.poll(Duration.ofMillis(500))) {
                    if (user.toString().equals(r.key()) && new String(r.value()).contains("not json")) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Test
    void walletRequiresGatewayIdentity() {
        assertThat(http.getForEntity("/api/wallet/me", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
