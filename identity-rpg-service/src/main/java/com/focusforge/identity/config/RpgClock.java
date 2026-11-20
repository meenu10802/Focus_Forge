package com.focusforge.identity.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** "Today" in the game's timezone. Injectable so tests can time-travel. */
@Component
public class RpgClock {

    private final Clock clock;

    public RpgClock(RpgProperties props, Clock clock) {
        this.clock = clock.withZone(props.timezone());
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
