package com.focusforge.identity.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.UUID;

/** Idempotent-consumer guard. */
public interface ProcessedEventRepository extends Repository<ProcessedEventRepository.Marker, UUID> {

    /** @return 1 the first time an event is seen, 0 for a redelivery. Joins the caller's transaction. */
    @Modifying
    @Query(value = "INSERT INTO processed_events (event_id) VALUES (:eventId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int markProcessed(UUID eventId);

    @Entity
    @Table(name = "processed_events")
    class Marker {
        @Id
        @Column(name = "event_id")
        UUID eventId;
    }
}
