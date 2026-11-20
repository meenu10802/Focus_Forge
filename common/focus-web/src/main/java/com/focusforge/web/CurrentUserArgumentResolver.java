package com.focusforge.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String raw = webRequest.getHeader(GatewayHeaders.USER_ID);
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated",
                    "Missing " + GatewayHeaders.USER_ID + " header; call this API through the gateway.");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Malformed user id.");
        }
    }
}
