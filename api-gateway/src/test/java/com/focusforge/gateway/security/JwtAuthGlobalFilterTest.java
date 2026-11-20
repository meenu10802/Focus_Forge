package com.focusforge.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-42";
    private static final String ISSUER = "focusforge";

    private final JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(
            new JwtVerifier(new SecurityProperties.Jwt(SECRET, ISSUER)),
            new SecurityProperties.Paths(List.of("/api/auth/**", "/api/health"),
                    Map.of("/api/admin/**", "ADMIN")));

    /** Captures the request the filter hands downstream; null if the chain was never called. */
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = ex -> {
        forwarded.set(ex);
        return Mono.empty();
    };

    private static String token(UUID userId, String role, String secret, String issuer, Instant exp) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(Date.from(exp))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String validToken(UUID userId, String role) {
        return token(userId, role, SECRET, ISSUER, Instant.now().plusSeconds(600));
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> req) {
        var exchange = MockServerWebExchange.from(req);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void publicPathPassesWithoutTokenAndStripsSpoofedHeaders() {
        run(MockServerHttpRequest.post("/api/auth/login").header("X-User-Id", "spoofed"));

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
    }

    @Test
    void missingTokenIs401ProblemJson() {
        var ex = run(MockServerHttpRequest.get("/api/profile/me"));

        assertThat(forwarded.get()).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
        assertThat(ex.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void validTokenForwardsIdentityAndReplacesSpoofedHeader() {
        UUID id = UUID.randomUUID();
        run(MockServerHttpRequest.get("/api/profile/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(id, "PLAYER"))
                .header("X-User-Id", UUID.randomUUID().toString()));

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get("X-User-Id")).containsExactly(id.toString());
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("PLAYER");
    }

    @Test
    void tamperedExpiredOrForeignTokensAre401() {
        UUID id = UUID.randomUUID();
        String wrongKey = token(id, "PLAYER", "another-secret-another-secret-another-1", ISSUER,
                Instant.now().plusSeconds(600));
        String expired = token(id, "PLAYER", SECRET, ISSUER, Instant.now().minusSeconds(3600));
        String wrongIssuer = token(id, "PLAYER", SECRET, "evil", Instant.now().plusSeconds(600));

        for (String t : List.of(wrongKey, expired, wrongIssuer, "garbage")) {
            forwarded.set(null);
            var ex = run(MockServerHttpRequest.get("/api/quests")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + t));
            assertThat(ex.getResponse().getStatusCode()).as(t).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(forwarded.get()).isNull();
        }
    }

    @Test
    void adminPathsRequireAdminRole() {
        UUID id = UUID.randomUUID();
        var asPlayer = run(MockServerHttpRequest.get("/api/admin/rewards")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(id, "PLAYER")));
        assertThat(asPlayer.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded.get()).isNull();

        run(MockServerHttpRequest.get("/api/admin/rewards")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken(id, "ADMIN")));
        assertThat(forwarded.get()).isNotNull();
    }
}
