package com.focusforge.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Google Gemini generateContent with a JSON response MIME type. */
@Component("geminiProvider")
public class GeminiProvider implements AiProvider {

    private final AiProperties.Endpoint cfg;
    private final RestClient http;

    public GeminiProvider(AiProperties props, RestClient.Builder builder) {
        this.cfg = props.gemini();
        this.http = builder.clone().baseUrl(cfg.baseUrl()).build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt) {
        if (!cfg.hasKey()) {
            throw new AiNotConfiguredException(name());
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "responseMimeType", "application/json"));

        JsonNode res = http.post()
                .uri("/models/{model}:generateContent", cfg.model())
                // Header rather than ?key= so the key never lands in access logs.
                .header("x-goog-api-key", cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode text = res == null ? null : res.at("/candidates/0/content/parts/0/text");
        if (text == null || text.isMissingNode() || text.isNull()) {
            throw new RestClientException("Gemini response had no text part");
        }
        return text.asText();
    }
}
