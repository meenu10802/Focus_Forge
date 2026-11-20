package com.focusforge.identity.profile;

import com.focusforge.identity.config.RpgClock;
import com.focusforge.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final RpgClock clock;

    public ProfileController(ProfileService profileService, RpgClock clock) {
        this.profileService = profileService;
        this.clock = clock;
    }

    @GetMapping("/me")
    public DashboardDto me(@CurrentUser UUID userId) {
        return profileService.getDashboard(userId, clock.today());
    }
}
