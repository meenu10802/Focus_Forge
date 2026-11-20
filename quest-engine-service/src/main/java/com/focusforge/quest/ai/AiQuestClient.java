package com.focusforge.quest.ai;

import com.focusforge.quest.quest.Difficulty;
import com.focusforge.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls ai-quest-service through Eureka (client-side load balancing).
 * ai-quest already degrades to a template when the model is down; this client
 * only has to handle ai-quest itself being unreachable.
 */
@Component
public class AiQuestClient {

    private static final Logger log = LoggerFactory.getLogger(AiQuestClient.class);

    public enum Source { AI_GENERATED, FALLBACK }

    public record PlannedTask(String title, String description, int minutes, Difficulty difficulty) {
    }

    public record Plan(String questTitle, Source source, String provider, List<PlannedTask> tasks) {
    }

    @ConfigurationProperties("focus.ai-client")
    public record Props(String baseUrl, Duration readTimeout) {
    }

    @Configuration
    static class ClientConfig {
        @Bean
        @LoadBalanced
        RestClient.Builder loadBalancedRestClientBuilder() {
            return RestClient.builder();
        }
    }

    private final RestClient http;

    public AiQuestClient(@LoadBalanced RestClient.Builder builder, Props props) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(props.readTimeout());
        this.http = builder.clone()
                .baseUrl(props.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    public Plan questline(String goal) {
        return call("/internal/ai/questline", Map.of("goal", goal));
    }

    public Plan split(String taskTitle, String taskDescription, String goal) {
        return call("/internal/ai/split", Map.of(
                "taskTitle", taskTitle,
                "taskDescription", taskDescription == null ? "" : taskDescription,
                "goal", goal == null ? "" : goal));
    }

    private Plan call(String path, Map<String, String> body) {
        try {
            Plan plan = http.post().uri(path).body(body).retrieve().body(Plan.class);
            if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
                throw new RestClientException("Empty plan from ai-quest-service");
            }
            return plan;
        } catch (RestClientException | IllegalStateException e) {
            // IllegalStateException: load balancer found no ai-quest-service instance
            log.warn("ai-quest-service call to {} failed: {}", path, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Quest Generator Unavailable",
                    "The quest generator is offline right now. Please try again in a minute.");
        }
    }
}
