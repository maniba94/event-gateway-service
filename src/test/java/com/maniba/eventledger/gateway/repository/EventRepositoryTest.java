package com.maniba.eventledger.gateway.repository;

import com.maniba.eventledger.gateway.entity.EventRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Test
    void findByAccountIdOrderByEventTimestampAsc_shouldReturnSortedEvents() {
        EventRecord first = EventRecord.builder()
                .eventId("evt-10")
                .accountId("acct-10")
                .type(com.maniba.eventledger.gateway.entity.EventType.CREDIT)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .status(com.maniba.eventledger.gateway.entity.EventStatus.RECEIVED)
                .build();

        EventRecord second = EventRecord.builder()
                .eventId("evt-11")
                .accountId("acct-10")
                .type(com.maniba.eventledger.gateway.entity.EventType.DEBIT)
                .amount(BigDecimal.ONE)
                .currency("USD")
                .eventTimestamp(Instant.parse("2026-01-02T00:00:00Z"))
                .status(com.maniba.eventledger.gateway.entity.EventStatus.RECEIVED)
                .build();

        eventRepository.save(first);
        eventRepository.save(second);

        var results = eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-10");

        assertThat(results).extracting(EventRecord::getEventId).containsExactly("evt-10", "evt-11");
    }
}
