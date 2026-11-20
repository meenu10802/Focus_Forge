package com.focusforge.bank.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Reconciliation: signed sum of the ledger, which must equal the wallet balance. */
    @Query("select coalesce(sum(case when e.type = com.focusforge.bank.wallet.LedgerEntry.Type.CREDIT "
            + "then e.amount else -e.amount end), 0) from LedgerEntry e where e.userId = :userId")
    long ledgerBalance(UUID userId);
}
