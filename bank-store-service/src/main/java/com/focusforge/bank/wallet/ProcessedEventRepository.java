package com.focusforge.bank.wallet;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/** Idempotent-consumer guard. */
public interface ProcessedEventRepository extends Repository<ProcessedEventRepository.Marker, UUID> {

    /**
     * @return 1 if this is the first time we see the event, 0 if it was already processed.
     * Runs in the caller's transaction, so the mark and the business change commit (or roll back) together.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id) VALUES (:eventId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int markProcessed(UUID eventId);

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "processed_events")
    class Marker {
        @jakarta.persistence.Id
        @jakarta.persistence.Column(name = "event_id")
        UUID eventId;
    }
}
