package com.focusforge.quest.focus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {

    @Query("select s from FocusSession s join fetch s.task t join fetch t.quest "
            + "where s.userId = :userId and s.status = com.focusforge.quest.focus.FocusSession.Status.ACTIVE")
    Optional<FocusSession> findActive(UUID userId);
}
