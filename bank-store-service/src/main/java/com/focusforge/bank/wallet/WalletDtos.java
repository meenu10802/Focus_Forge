package com.focusforge.bank.wallet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletDto(long balance, Instant updatedAt) {
    }

    public record LedgerEntryDto(UUID id, LedgerEntry.Type type, long amount, long balanceAfter, String reason,
                                 UUID refId, Instant createdAt) {

        static LedgerEntryDto of(LedgerEntry e) {
            return new LedgerEntryDto(e.getId(), e.getType(), e.getAmount(), e.getBalanceAfter(), e.getReason(),
                    e.getRefId(), e.getCreatedAt());
        }
    }

    /** Stable page shape (Spring's PageImpl JSON is not a stable contract). */
    public record PageDto<T>(List<T> content, int page, int size, long totalElements) {
    }
}
