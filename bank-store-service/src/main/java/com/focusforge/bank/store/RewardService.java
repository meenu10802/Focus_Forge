package com.focusforge.bank.store;

import com.focusforge.bank.store.StoreDtos.RedemptionDto;
import com.focusforge.bank.store.StoreDtos.RewardDto;
import com.focusforge.bank.store.StoreDtos.RewardRequest;
import com.focusforge.web.ApiException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Catalogue management: players own custom rewards; admins manage global ones. */
@Service
public class RewardService {

    private static final int MAX_CUSTOM_REWARDS = 50;

    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;

    public RewardService(RewardRepository rewards, RedemptionRepository redemptions) {
        this.rewards = rewards;
        this.redemptions = redemptions;
    }

    @Transactional(readOnly = true)
    public List<RewardDto> visibleTo(UUID userId) {
        return rewards.findVisibleTo(userId).stream().map(r -> RewardDto.of(r, userId)).toList();
    }

    @Transactional
    public RewardDto createCustom(UUID userId, RewardRequest req) {
        long owned = rewards.countByOwnerUserIdAndActiveTrue(userId);
        if (owned >= MAX_CUSTOM_REWARDS) {
            throw ApiException.conflict("You can have at most " + MAX_CUSTOM_REWARDS + " custom rewards.");
        }
        var reward = new Reward(userId, req.name(), req.description(), req.emoji(), req.price(), req.stock());
        return RewardDto.of(rewards.save(reward), userId);
    }

    @Transactional
    public RewardDto updateCustom(UUID userId, UUID rewardId, RewardRequest req) {
        Reward reward = owned(userId, rewardId);
        reward.update(req.name(), req.description(), req.emoji(), req.price(), req.stock());
        return RewardDto.of(reward, userId);
    }

    @Transactional
    public void deleteCustom(UUID userId, UUID rewardId) {
        owned(userId, rewardId).deactivate(); // soft delete keeps redemption history intact
    }

    @Transactional
    public RewardDto createGlobal(RewardRequest req) {
        return RewardDto.of(rewards.save(new Reward(null, req.name(), req.description(), req.emoji(),
                req.price(), req.stock())), null);
    }

    @Transactional
    public RewardDto updateGlobal(UUID rewardId, RewardRequest req) {
        Reward reward = global(rewardId);
        reward.update(req.name(), req.description(), req.emoji(), req.price(), req.stock());
        return RewardDto.of(reward, null);
    }

    @Transactional
    public void deleteGlobal(UUID rewardId) {
        global(rewardId).deactivate();
    }

    @Transactional(readOnly = true)
    public List<RedemptionDto> history(UUID userId) {
        return redemptions.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(50)).stream()
                .map(RedemptionDto::of).toList();
    }

    private Reward owned(UUID userId, UUID rewardId) {
        return rewards.findById(rewardId)
                .filter(Reward::isActive)
                .filter(r -> r.isOwnedBy(userId))
                .orElseThrow(() -> ApiException.notFound("Custom reward not found."));
    }

    private Reward global(UUID rewardId) {
        return rewards.findById(rewardId)
                .filter(Reward::isActive)
                .filter(Reward::isGlobal)
                .orElseThrow(() -> ApiException.notFound("Global reward not found."));
    }
}
