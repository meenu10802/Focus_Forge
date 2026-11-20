package com.focusforge.bank.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /** Race-free "create if missing": two first-ever credits can't both insert. */
    @Modifying
    @Query(value = "INSERT INTO wallets (user_id, balance, version, updated_at) VALUES (:userId, 0, 0, now()) "
            + "ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    void ensureExists(UUID userId);
}
