package com.focusforge.bank.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardRepository extends JpaRepository<Reward, UUID> {

    /** Global rewards plus the player's own, cheapest first. */
    @Query("select r from Reward r where r.active = true and (r.ownerUserId is null or r.ownerUserId = :userId) "
            + "order by r.price, r.name")
    List<Reward> findVisibleTo(UUID userId);

    @Query("select r from Reward r where r.id = :id and r.active = true "
            + "and (r.ownerUserId is null or r.ownerUserId = :userId)")
    Optional<Reward> findVisible(UUID id, UUID userId);

    long countByOwnerUserIdAndActiveTrue(UUID ownerUserId);
}
