package com.focusforge.bank.store;

import com.focusforge.bank.store.StoreDtos.RedemptionDto;
import com.focusforge.bank.store.StoreDtos.RewardDto;
import com.focusforge.bank.store.StoreDtos.RewardRequest;
import com.focusforge.web.ApiException;
import com.focusforge.web.CurrentUser;
import com.focusforge.web.GatewayHeaders;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class StoreController {

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final RewardService rewards;
    private final RedemptionService redemptions;

    public StoreController(RewardService rewards, RedemptionService redemptions) {
        this.rewards = rewards;
        this.redemptions = redemptions;
    }

    // --- player ------------------------------------------------------------

    @GetMapping("/api/store/rewards")
    public List<RewardDto> list(@CurrentUser UUID userId) {
        return rewards.visibleTo(userId);
    }

    @PostMapping("/api/store/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    public RewardDto create(@CurrentUser UUID userId, @Valid @RequestBody RewardRequest req) {
        return rewards.createCustom(userId, req);
    }

    @PutMapping("/api/store/rewards/{rewardId}")
    public RewardDto update(@CurrentUser UUID userId, @PathVariable UUID rewardId,
                            @Valid @RequestBody RewardRequest req) {
        return rewards.updateCustom(userId, rewardId, req);
    }

    @DeleteMapping("/api/store/rewards/{rewardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID rewardId) {
        rewards.deleteCustom(userId, rewardId);
    }

    @PostMapping("/api/store/rewards/{rewardId}/redeem")
    public ResponseEntity<RedemptionDto> redeem(@CurrentUser UUID userId, @PathVariable UUID rewardId,
                                                @RequestHeader("Idempotency-Key") String key) {
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key",
                    "Idempotency-Key must be 8-100 characters of [A-Za-z0-9_-]; a UUID is ideal.");
        }
        var result = redemptions.redeem(userId, rewardId, key);
        var response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.redemption());
    }

    @GetMapping("/api/store/redemptions")
    public List<RedemptionDto> history(@CurrentUser UUID userId) {
        return rewards.history(userId);
    }

    // --- admin (gateway enforces ADMIN; checked again here as defence in depth) ---

    @PostMapping("/api/admin/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    public RewardDto createGlobal(@RequestHeader(value = GatewayHeaders.USER_ROLES, required = false) String roles,
                                  @Valid @RequestBody RewardRequest req) {
        requireAdmin(roles);
        return rewards.createGlobal(req);
    }

    @PutMapping("/api/admin/rewards/{rewardId}")
    public RewardDto updateGlobal(@RequestHeader(value = GatewayHeaders.USER_ROLES, required = false) String roles,
                                  @PathVariable UUID rewardId, @Valid @RequestBody RewardRequest req) {
        requireAdmin(roles);
        return rewards.updateGlobal(rewardId, req);
    }

    @DeleteMapping("/api/admin/rewards/{rewardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobal(@RequestHeader(value = GatewayHeaders.USER_ROLES, required = false) String roles,
                             @PathVariable UUID rewardId) {
        requireAdmin(roles);
        rewards.deleteGlobal(rewardId);
    }

    private static void requireAdmin(String roles) {
        boolean admin = roles != null && Arrays.asList(roles.split(",")).contains("ADMIN");
        if (!admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Requires role ADMIN.");
        }
    }
}
