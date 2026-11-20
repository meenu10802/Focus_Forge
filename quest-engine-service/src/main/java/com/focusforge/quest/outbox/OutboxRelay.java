package com.focusforge.quest.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and publishes to Kafka (at-least-once). If the process dies
 * after the send but before marking the row, the event is sent again; that's
 * why every consumer de-duplicates on {@code eventId}.
 * <p>
 * Stops a batch at the first failure so events for a player are never
 * published out of order.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repo;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final int batchSize;

    public OutboxRelay(OutboxRepository repo, KafkaTemplate<String, Object> kafka, ObjectMapper mapper,
                       TransactionTemplate tx, @Value("${focus.outbox.batch-size:100}") int batchSize) {
        this.repo = repo;
        this.kafka = kafka;
        this.mapper = mapper;
        this.tx = tx;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${focus.outbox.poll-interval-ms:500}")
    public void relay() {
        Integer sent;
        do {
            sent = tx.execute(status -> publishBatch());
        } while (sent != null && sent == batchSize);
    }

    private int publishBatch() {
        var batch = repo.lockNextBatch(batchSize);
        int sent = 0;
        for (OutboxEvent e : batch) {
            try {
                Object event = mapper.readValue(e.getPayload(), Outbox.EVENT_TYPES.get(e.getEventType()));
                kafka.send(e.getTopic(), e.getMsgKey(), event).get(10, TimeUnit.SECONDS);
                e.markPublished(Instant.now());
                sent++;
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                e.markFailed(ex.getMessage());
                log.warn("Outbox publish failed for {} ({}); will retry: {}", e.getId(), e.getTopic(),
                        ex.getMessage());
                break;
            }
        }
        return sent;
    }
}
