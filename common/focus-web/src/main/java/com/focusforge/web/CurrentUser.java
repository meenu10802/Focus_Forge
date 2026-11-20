package com.focusforge.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated player's id (a {@link java.util.UUID}) from the
 * {@value GatewayHeaders#USER_ID} header. Responds 401 when absent or malformed.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * DashboardDto me(@CurrentUser UUID userId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
