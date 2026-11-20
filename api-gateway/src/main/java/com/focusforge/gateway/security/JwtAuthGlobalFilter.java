package com.focusforge.gateway.security;

import com.focusforge.gateway.security.JwtVerifier.AuthenticatedUser;
import com.focusforge.gateway.security.JwtVerifier.InvalidTokenException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Edge authentication for every routed request:
 * <ol>
 *   <li>Always strips client-supplied identity headers (anti-spoofing).</li>
 *   <li>Public paths pass through anonymously.</li>
 *   <li>Otherwise requires a valid {@code Authorization: Bearer} JWT -> 401.</li>
 *   <li>Enforces role-restricted paths -> 403.</li>
 *   <li>Forwards {@code X-User-Id} / {@code X-User-Roles} to the downstream service.</li>
 * </ol>
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";
    private static final String BEARER = "Bearer ";

    private final JwtVerifier verifier;
    private final SecurityProperties.Paths paths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(JwtVerifier verifier, SecurityProperties.Paths paths) {
        this.verifier = verifier;
        this.paths = paths;
    }

    @Override
    public int getOrder() {
        return -100; // before routing/load-balancing filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLES);
                })
                .build();
        String path = request.getPath().value();

        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(path)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing bearer token.");
        }

        AuthenticatedUser user;
        try {
            user = verifier.verify(header.substring(BEARER.length()).trim());
        } catch (InvalidTokenException e) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token.");
        }

        for (Map.Entry<String, String> rule : paths.rolePaths().entrySet()) {
            if (matcher.match(rule.getKey(), path) && !user.hasRole(rule.getValue())) {
                return reject(exchange, HttpStatus.FORBIDDEN, "Requires role " + rule.getValue() + ".");
            }
        }

        ServerHttpRequest authenticated = request.mutate()
                .header(USER_ID, user.userId().toString())
                .header(USER_ROLES, String.join(",", user.roles()))
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private boolean isPublic(String path) {
        return paths.publicPaths().stream().anyMatch(p -> matcher.match(p, path));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        String body = """
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail,
                        exchange.getRequest().getPath().value());
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
