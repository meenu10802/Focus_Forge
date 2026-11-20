package com.focusforge.bank.wallet;

import com.focusforge.bank.wallet.WalletDtos.LedgerEntryDto;
import com.focusforge.bank.wallet.WalletDtos.PageDto;
import com.focusforge.bank.wallet.WalletDtos.WalletDto;
import com.focusforge.events.TaskCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final ProcessedEventRepository processed;

    public WalletService(WalletRepository wallets, LedgerRepository ledger, ProcessedEventRepository processed) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.processed = processed;
    }

    /**
     * Credits coins for a completed task. Kafka delivers at-least-once, so the
     * same event may arrive twice; the processed_events insert makes the second
     * delivery a no-op. All three writes share one transaction.
     *
     * @return false if the event was a duplicate
     */
    @Transactional
    public boolean creditForTask(TaskCompletedEvent event) {
        if (processed.markProcessed(event.eventId()) == 0) {
            log.info("Duplicate TaskCompletedEvent {} ignored", event.eventId());
            return false;
        }
        if (event.coinsEarned() <= 0) {
            return true;
        }
        wallets.ensureExists(event.userId());
        Wallet wallet = wallets.findById(event.userId()).orElseThrow();
        Instant now = Instant.now();
        long balance = wallet.credit(event.coinsEarned(), now);
        ledger.save(LedgerEntry.credit(event.userId(), event.coinsEarned(), balance, "Completed a micro-quest",
                event.taskId(), event.eventId(), now));
        log.info("Credited {} coins to {} (balance {})", event.coinsEarned(), event.userId(), balance);
        return true;
    }

    @Transactional(readOnly = true)
    public WalletDto wallet(UUID userId) {
        return wallets.findById(userId)
                .map(w -> new WalletDto(w.getBalance(), w.getUpdatedAt()))
                .orElse(new WalletDto(0, null));
    }

    @Transactional(readOnly = true)
    public PageDto<LedgerEntryDto> transactions(UUID userId, int page, int size) {
        var result = ledger.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return new PageDto<>(result.getContent().stream().map(LedgerEntryDto::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }
}
