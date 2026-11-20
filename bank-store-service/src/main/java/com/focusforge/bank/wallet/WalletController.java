package com.focusforge.bank.wallet;

import com.focusforge.bank.wallet.WalletDtos.LedgerEntryDto;
import com.focusforge.bank.wallet.WalletDtos.PageDto;
import com.focusforge.bank.wallet.WalletDtos.WalletDto;
import com.focusforge.web.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@Validated
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/me")
    public WalletDto me(@CurrentUser UUID userId) {
        return wallets.wallet(userId);
    }

    @GetMapping("/me/transactions")
    public PageDto<LedgerEntryDto> transactions(@CurrentUser UUID userId,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return wallets.transactions(userId, page, size);
    }
}
