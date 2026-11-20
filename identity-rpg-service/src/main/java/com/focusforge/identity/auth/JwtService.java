package com.focusforge.identity.auth;

import com.focusforge.identity.config.JwtProperties;
import com.focusforge.identity.user.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues HS256 access tokens. The gateway verifies them with the same secret.
 * Claims: sub = userId, roles = [PLAYER|ADMIN], name = display name.
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.ttl());
        String token = Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .claim("roles", List.of(user.getRole().name()))
                .claim("name", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, props.ttl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
