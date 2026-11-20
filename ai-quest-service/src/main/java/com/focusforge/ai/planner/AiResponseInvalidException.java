package com.focusforge.ai.planner;

/** The model answered, but not with a usable plan. Retryable: a second sample usually works. */
public class AiResponseInvalidException extends RuntimeException {

    public AiResponseInvalidException(String message) {
        super(message);
    }

    public AiResponseInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
