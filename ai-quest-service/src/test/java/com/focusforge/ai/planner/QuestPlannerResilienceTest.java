package com.focusforge.ai.planner;

import com.focusforge.ai.planner.PlanDtos.Source;
import com.focusforge.ai.provider.AiNotConfiguredException;
import com.focusforge.ai.provider.AiProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real Resilience4j aspects (Retry -> CircuitBreaker -> provider) in a Spring context. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "focus.ai.provider=fake",
        "resilience4j.retry.instances.aiProvider.wait-duration=5ms",
})
@AutoConfigureMockMvc
class QuestPlannerResilienceTest {

    static final String GOOD = """
            {"questTitle":"Spring Security","tasks":[
              {"title":"Read the filter chain docs","description":"","minutes":12,"difficulty":"EASY"},
              {"title":"Add a SecurityFilterChain bean","description":"","minutes":15,"difficulty":"MEDIUM"},
              {"title":"Write a JWT filter","description":"","minutes":15,"difficulty":"HARD"}]}""";

    /** Scriptable provider: each call pops the next behaviour; counts calls. */
    static class FakeProvider implements AiProvider {
        final Deque<Supplier<String>> script = new ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public String completeJson(String systemPrompt, String userPrompt) {
            calls.incrementAndGet();
            Supplier<String> next = script.poll();
            if (next == null) {
                throw new ResourceAccessException("connection refused");
            }
            return next.get();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        FakeProvider fakeProvider() {
            return new FakeProvider();
        }
    }

    @Autowired
    QuestPlanner planner;
    @Autowired
    FakeProvider fake;
    @Autowired
    CircuitBreakerRegistry breakers;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        fake.script.clear();
        fake.calls.set(0);
        breakers.circuitBreaker("aiProvider").reset();
    }

    @Test
    void healthyProviderYieldsAiPlan() {
        fake.script.add(() -> GOOD);

        var plan = planner.questline("Learn Spring Security in 3 days");

        assertThat(plan.source()).isEqualTo(Source.AI_GENERATED);
        assertThat(plan.provider()).isEqualTo("fake");
        assertThat(plan.tasks()).hasSize(3);
        assertThat(fake.calls).hasValue(1);
    }

    @Test
    void malformedOutputIsRetriedOnce() {
        fake.script.add(() -> "I'm sorry, I can't do JSON today");
        fake.script.add(() -> GOOD);

        var plan = planner.questline("Learn Spring Security");

        assertThat(plan.source()).isEqualTo(Source.AI_GENERATED);
        assertThat(fake.calls).hasValue(2);
    }

    @Test
    void persistentFailureFallsBackToTemplate() {
        // empty script -> every call throws a connection error
        var plan = planner.questline("Learn Kafka");

        assertThat(plan.source()).isEqualTo(Source.FALLBACK);
        assertThat(plan.tasks()).hasSize(5);
        assertThat(plan.tasks().get(0).description()).contains("Learn Kafka");
        assertThat(fake.calls).hasValue(2); // original + 1 retry
    }

    @Test
    void missingApiKeyFallsBackImmediatelyWithoutRetry() {
        fake.script.add(() -> {
            throw new AiNotConfiguredException("fake");
        });

        assertThat(planner.questline("Learn Go").source()).isEqualTo(Source.FALLBACK);
        assertThat(fake.calls).hasValue(1);
        assertThat(breakers.circuitBreaker("aiProvider").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void openBreakerShortCircuitsWithoutCallingProvider() {
        // 2 requests x 2 attempts = 4 failures >= minimum-number-of-calls at 100% failure rate -> OPEN
        planner.questline("a");
        planner.questline("b");
        assertThat(breakers.circuitBreaker("aiProvider").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int before = fake.calls.get();
        fake.script.add(() -> GOOD); // provider has "recovered", but the breaker hasn't let us find out yet
        var plan = planner.questline("c");

        assertThat(plan.source()).isEqualTo(Source.FALLBACK);
        assertThat(fake.calls.get()).isEqualTo(before);
    }

    @Test
    void splitFallsBackToThreeMicroSteps() {
        var plan = planner.split("Configure CORS", "", "Learn Spring Security");
        assertThat(plan.source()).isEqualTo(Source.FALLBACK);
        assertThat(plan.tasks()).hasSize(3).allMatch(t -> t.minutes() <= 5);
    }

    @Test
    void internalEndpointValidatesInput() throws Exception {
        mvc.perform(post("/internal/ai/questline").contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.goal").exists());

        mvc.perform(post("/internal/ai/questline").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Learn Docker\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.tasks.length()").value(5));
    }
}
