package com.focusforge.ai.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusforge.ai.planner.PlanDtos.PlanResponse;
import com.focusforge.ai.planner.PlanDtos.Source;
import com.focusforge.ai.provider.AiClientConfig;
import com.focusforge.ai.provider.AiProperties;
import com.focusforge.ai.provider.AiProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resilience layering (Resilience4j aspect order: Retry wraps CircuitBreaker):
 * <pre>
 *   Retry(max 2, fallback)  ->  CircuitBreaker(aiProvider)  ->  provider call + parse
 * </pre>
 * The fallback sits on {@code @Retry}, the OUTER aspect. Had it been on
 * {@code @CircuitBreaker}, the breaker would swallow the first failure into
 * the fallback and Retry would never get a second attempt. When the breaker is
 * open, {@code CallNotPermittedException} is not retried and goes straight to
 * the template, so a dead provider costs ~0 ms instead of a timeout per request.
 */
@Service
public class QuestPlanner {

    private static final Logger log = LoggerFactory.getLogger(QuestPlanner.class);
    static final String RESILIENCE_NAME = "aiProvider";

    private final AiProvider provider;
    private final PlanParser parser;
    private final int maxGoalLength;

    public QuestPlanner(List<AiProvider> providers, ObjectMapper mapper, AiProperties props) {
        this.provider = AiClientConfig.select(providers, props.provider());
        log.info("AI planner using provider '{}'", provider.name());
        this.parser = new PlanParser(mapper);
        this.maxGoalLength = props.maxGoalLength();
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "questlineFallback")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public PlanResponse questline(String goal) {
        String cleanGoal = sanitize(goal);
        String raw = provider.completeJson(Prompts.QUESTLINE_SYSTEM, Prompts.questlineUser(cleanGoal));
        var plan = parser.parse(raw, PlanParser.QUESTLINE, cleanGoal);
        return new PlanResponse(plan.questTitle(), Source.AI_GENERATED, provider.name(), plan.tasks());
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "splitFallback")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public PlanResponse split(String taskTitle, String taskDescription, String goal) {
        String raw = provider.completeJson(Prompts.SPLIT_SYSTEM,
                Prompts.splitUser(sanitize(taskTitle), sanitize(taskDescription), sanitize(goal)));
        var plan = parser.parse(raw, PlanParser.SPLIT, taskTitle);
        return new PlanResponse(plan.questTitle(), Source.AI_GENERATED, provider.name(), plan.tasks());
    }

    /*
     * Fallbacks are invoked reflectively by Resilience4j ON THE SPRING PROXY. They must not be
     * private: a private method can't be overridden by the CGLIB proxy, so it would run on the
     * proxy instance itself, whose fields (maxGoalLength, ...) were never initialised.
     */
    @SuppressWarnings("unused")
    public PlanResponse questlineFallback(String goal, Throwable cause) {
        log.warn("AI questline unavailable ({}: {}); serving template", cause.getClass().getSimpleName(),
                cause.getMessage());
        return FallbackTemplates.questline(sanitize(goal));
    }

    @SuppressWarnings("unused")
    public PlanResponse splitFallback(String taskTitle, String taskDescription, String goal, Throwable cause) {
        log.warn("AI split unavailable ({}: {}); serving template", cause.getClass().getSimpleName(),
                cause.getMessage());
        return FallbackTemplates.split(sanitize(taskTitle));
    }

    /** Collapse whitespace, drop our delimiter tags, cap length. */
    private String sanitize(String s) {
        if (s == null) {
            return "";
        }
        String clean = s.replaceAll("(?i)</?(goal|task)>", "").replaceAll("\\s+", " ").strip();
        return clean.length() <= maxGoalLength ? clean : clean.substring(0, maxGoalLength);
    }
}
