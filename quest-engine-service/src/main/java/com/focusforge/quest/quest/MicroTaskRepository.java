package com.focusforge.quest.quest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MicroTaskRepository extends JpaRepository<MicroTask, UUID> {

    Optional<MicroTask> findByIdAndUserId(UUID id, UUID userId);
}
