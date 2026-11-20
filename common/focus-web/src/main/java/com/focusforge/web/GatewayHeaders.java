package com.focusforge.web;

/**
 * Headers injected by api-gateway after JWT validation. The gateway strips any
 * client-supplied copies, so downstream services can trust them — provided the
 * service is only reachable through the gateway.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";

    private GatewayHeaders() {
    }
}
