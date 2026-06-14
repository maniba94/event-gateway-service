package com.maniba.eventledger.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EventMetrics {

    private final Counter eventsCreated;
    private final Counter eventsDuplicate;
    private final Counter eventsFailed;

    public EventMetrics(MeterRegistry registry) {
        this.eventsCreated = Counter.builder("events.created.count")
                .description("Number of successfully created events")
                .register(registry);
        this.eventsDuplicate = Counter.builder("events.duplicate.count")
                .description("Number of duplicate events received")
                .register(registry);
        this.eventsFailed = Counter.builder("events.failed.count")
                .description("Number of events failed due to account service or validation")
                .register(registry);
    }

    public void incrementCreated() {
        eventsCreated.increment();
    }

    public void incrementDuplicate() {
        eventsDuplicate.increment();
    }

    public void incrementFailed() {
        eventsFailed.increment();
    }
}
