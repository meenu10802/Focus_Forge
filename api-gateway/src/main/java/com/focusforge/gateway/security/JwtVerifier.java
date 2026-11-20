package com.focusforge.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Verifies HS256 tokens minted by identity-rpg-service. */
@Component
public class JwtVerifier {

    private final JwtParser parser;

    public JwtVerifier(SecurityProperties.Jwt props) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(props.issuer())
                .clockSkewSeconds(30)
                .build();
    }

    /**
     * @throws InvalidTokenException if the signature, issuer, expiry or claims are bad
     */
    public AuthenticatedUser verify(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            List<?> rawRoles = claims.get("roles", List.class);
            List<String> roles = rawRoles == null ? List.of()
                    : rawRoles.stream().map(String::valueOf).toList();
            return new AuthenticatedUser(userId, roles);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException(e);
        }
    }

    public record AuthenticatedUser(UUID userId, List<String> roles) {

        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }

    public static class InvalidTokenException extends RuntimeException {

        InvalidTokenException(Throwable cause) {
            super(cause);
        }
    }
}
