package com.focusforge.identity.auth;

import com.focusforge.identity.auth.AuthDtos.AuthResponse;
import com.focusforge.identity.auth.AuthDtos.LoginRequest;
import com.focusforge.identity.auth.AuthDtos.RegisterRequest;
import com.focusforge.identity.auth.AuthDtos.UserSummary;
import com.focusforge.identity.config.RpgClock;
import com.focusforge.identity.config.RpgProperties;
import com.focusforge.identity.profile.PlayerProfile;
import com.focusforge.identity.profile.PlayerProfileRepository;
import com.focusforge.identity.user.Role;
import com.focusforge.identity.user.User;
import com.focusforge.identity.user.UserRepository;
import com.focusforge.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password.";

    private final UserRepository users;
    private final PlayerProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RpgProperties rpg;
    private final RpgClock clock;
    /** Compared against when the email is unknown, so response time doesn't reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PlayerProfileRepository profiles, PasswordEncoder encoder,
                       JwtService jwt, RpgProperties rpg, RpgClock clock) {
        this.users = users;
        this.profiles = profiles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.rpg = rpg;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equalizer");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        Role role = rpg.isAdminEmail(email) ? Role.ADMIN : Role.PLAYER;
        User user = new User(email, encoder.encode(req.password()), req.displayName().trim(), role);
        try {
            users.saveAndFlush(user); // flush now so a concurrent duplicate hits the unique index here
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        profiles.save(PlayerProfile.newPlayer(user.getId(), rpg.maxHp(), clock.today()));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        var user = users.findByEmail(normalize(req.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = encoder.matches(req.password(), hash);
        if (user.isEmpty() || !matches) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        return toResponse(user.get());
    }

    private AuthResponse toResponse(User user) {
        var token = jwt.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(),
                new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name()));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
