package com.focusforge.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void taskCompletedEventRoundTripsThroughJson() throws Exception {
        var event = TaskCompletedEvent.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                20, 50, 10, 15);

        String json = mapper.writeValueAsString(event);
        var back = mapper.readValue(json, TaskCompletedEvent.class);

        assertThat(back).isEqualTo(event);
        assertThat(json).contains("\"coinsEarned\":20", "\"xpEarned\":50", "\"hpRestored\":10");
    }

    @Test
    void factoriesAssignFreshEventIds() {
        var user = UUID.randomUUID();
        assertThat(LevelUpEvent.of(user, 1, 2).eventId())
                .isNotEqualTo(LevelUpEvent.of(user, 1, 2).eventId());
    }
}
