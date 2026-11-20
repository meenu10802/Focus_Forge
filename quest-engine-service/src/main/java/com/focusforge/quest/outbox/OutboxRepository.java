package com.focusforge.quest.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest unpublished events, row-locked. SKIP LOCKED lets several relay
     * instances share the work without double-sending.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxEvent> lockNextBatch(int batchSize);

    long countByPublishedAtIsNull();
}
