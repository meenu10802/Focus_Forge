package com.focusforge.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Groq's OpenAI-compatible chat completions API in JSON mode. */
@Component("groqProvider")
public class GroqProvider implements AiProvider {

    private final AiProperties.Endpoint cfg;
    private final RestClient http;

    public GroqProvider(AiProperties props, RestClient.Builder builder) {
        this.cfg = props.groq();
        this.http = builder.clone().baseUrl(cfg.baseUrl()).build();
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt) {
        if (!cfg.hasKey()) {
            throw new AiNotConfiguredException(name());
        }
        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "temperature", 0.4,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        JsonNode res = http.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode content = res == null ? null : res.at("/choices/0/message/content");
        if (content == null || content.isMissingNode() || content.isNull()) {
            throw new RestClientException("Groq response had no message content");
        }
        return content.asText();
    }
}
