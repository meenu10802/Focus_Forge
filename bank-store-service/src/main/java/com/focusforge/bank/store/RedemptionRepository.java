package com.focusforge.bank.store;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RedemptionRepository extends JpaRepository<Redemption, UUID> {

    Optional<Redemption> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    List<Redemption> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);
}
