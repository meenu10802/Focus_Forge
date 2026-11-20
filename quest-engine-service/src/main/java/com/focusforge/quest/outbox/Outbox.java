package com.focusforge.quest.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusforge.events.FocusEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** Appends events to the outbox table inside the caller's transaction. */
@Component
public class Outbox {

    /** Only our own sealed event types can be stored and re-hydrated. */
    static final Map<String, Class<?>> EVENT_TYPES = Arrays.stream(FocusEvent.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, c -> c));

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public Outbox(OutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    /** MANDATORY: writing an event outside the business transaction would defeat the pattern. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, FocusEvent event) {
        try {
            repo.save(new OutboxEvent(event.eventId(), topic, event.userId().toString(),
                    event.getClass().getSimpleName(), mapper.writeValueAsString(event), Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + event, e);
        }
    }
}
