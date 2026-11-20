package com.focusforge.quest.quest;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestRepository extends JpaRepository<Quest, UUID> {

    List<Quest> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

    /** Ownership is part of the lookup: another player's quest is simply "not found". */
    Optional<Quest> findByIdAndUserId(UUID id, UUID userId);
}
