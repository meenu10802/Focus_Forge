package com.focusforge.web;

import org.springframework.http.HttpStatus;

/**
 * Domain error rendered as an RFC 7807 problem by {@link ProblemDetailsAdvice}.
 * Prefer the static factories for common cases.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Not Found", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Conflict", detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
