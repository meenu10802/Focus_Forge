package com.focusforge.ai.provider;

/**
 * A chat model that can be forced to answer with a single JSON object.
 * Implementations do transport only; parsing and validation live in the planner.
 */
public interface AiProvider {

    /** Stable id used in config ({@code focus.ai.provider}) and in responses. */
    String name();

    /**
     * @return the model's raw JSON text
     * @throws AiNotConfiguredException if no API key is set
     * @throws org.springframework.web.client.RestClientException on transport/HTTP errors
     */
    String completeJson(String systemPrompt, String userPrompt);
}
