package com.focusforge.ai.provider;

public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException(String provider) {
        super("No API key configured for AI provider '" + provider + "'");
    }
}
