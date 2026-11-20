package com.focusforge.bank.store;

import com.focusforge.bank.store.StoreDtos.RedemptionDto;
import com.focusforge.bank.wallet.LedgerEntry;
import com.focusforge.bank.wallet.LedgerRepository;
import com.focusforge.bank.wallet.Wallet;
import com.focusforge.bank.wallet.WalletRepository;
import com.focusforge.events.RewardRedeemedEvent;
import com.focusforge.events.Topics;
import com.focusforge.outbox.Outbox;
import com.focusforge.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redeems a reward as ONE local ACID transaction: stock--, wallet -= price,
 * ledger DEBIT, redemption row, outbox event. Wallet and inventory live in the
 * same database, so no saga is needed: it either all happens or none of it.
 * <p>
 * Concurrency: {@code @Version} on Reward and Wallet. A loser of a race gets
 * an optimistic-lock failure at commit; we retry the whole transaction a few
 * times with jitter. On retry it re-reads fresh state and typically discovers
 * "out of stock" or "insufficient coins": a clean 409, never negative stock.
 * <p>
 * Idempotency: the (user_id, idempotency_key) unique constraint. A repeat of
 * the same key returns the original redemption instead of charging again.
 */
@Service
public class RedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionService.class);
    private static final int MAX_ATTEMPTS = 4;

    public record Result(RedemptionDto redemption, boolean replayed) {
    }

    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final Outbox outbox;
    private final TransactionTemplate tx;

    public RedemptionService(RewardRepository rewards, RedemptionRepository redemptions, WalletRepository wallets,
                             LedgerRepository ledger, Outbox outbox, TransactionTemplate tx) {
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.wallets = wallets;
        this.ledger = ledger;
        this.outbox = outbox;
        this.tx = tx;
    }

    public Result redeem(UUID userId, UUID rewardId, String idempotencyKey) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> redeemOnce(userId, rewardId, idempotencyKey));
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
                // Lost a race (stock/wallet version, or same idempotency key in parallel). Re-read and try again.
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Redeem {} for {} still contended after {} attempts", rewardId, userId, attempt);
                    throw new ApiException(HttpStatus.CONFLICT, "Busy",
                            "Lots of people are grabbing this right now. Please try again.");
                }
                sleepJitter(attempt);
            }
        }
    }

    private Result redeemOnce(UUID userId, UUID rewardId, String key) {
        var previous = redemptions.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            if (!previous.get().getRewardId().equals(rewardId)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key Reused",
                        "This Idempotency-Key was already used to redeem a different reward.");
            }
            return new Result(RedemptionDto.of(previous.get()), true);
        }

        Reward reward = rewards.findVisible(rewardId, userId)
                .orElseThrow(() -> ApiException.notFound("Reward not found."));
        if (reward.getStock() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Out of Stock", "\"" + reward.getName() + "\" is sold out.");
        }

        wallets.ensureExists(userId);
        Wallet wallet = wallets.findById(userId).orElseThrow();
        if (wallet.getBalance() < reward.getPrice()) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient Coins",
                    "You need " + (reward.getPrice() - wallet.getBalance()) + " more coins for \""
                            + reward.getName() + "\". Go clear a quest!");
        }

        Instant now = Instant.now();
        reward.decrementStock();
        long balance = wallet.debit(reward.getPrice(), now);
        var redemption = redemptions.save(new Redemption(userId, reward, balance, key, now));
        ledger.save(LedgerEntry.debit(userId, reward.getPrice(), balance, "Redeemed: " + reward.getName(),
                redemption.getId(), now));
        outbox.append(Topics.REWARD_REDEEMED, RewardRedeemedEvent.of(userId, reward.getId(), reward.getName(),
                reward.getPrice(), reward.getStock(), balance));

        log.info("{} redeemed {} for {} coins (stock left {}, balance {})", userId, reward.getName(),
                reward.getPrice(), reward.getStock(), balance);
        return new Result(RedemptionDto.of(redemption), false);
    }

    private static void sleepJitter(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 40L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
