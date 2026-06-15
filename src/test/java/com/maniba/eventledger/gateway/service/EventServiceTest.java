package com.maniba.eventledger.gateway.service;

import com.maniba.eventledger.gateway.client.AccountClient;
import com.maniba.eventledger.gateway.dto.EventRequest;
import com.maniba.eventledger.gateway.dto.EventResponse;
import com.maniba.eventledger.gateway.entity.EventRecord;
import com.maniba.eventledger.gateway.entity.EventStatus;
import com.maniba.eventledger.gateway.entity.EventType;
import com.maniba.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.maniba.eventledger.gateway.exception.BadRequestException;
import com.maniba.eventledger.gateway.repository.EventRepository;
import com.maniba.eventledger.gateway.metrics.EventMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private EventRepository eventRepository;
    private AccountClient accountClient;
    private EventMetrics eventMetrics;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventRepository = Mockito.mock(EventRepository.class);
        accountClient = Mockito.mock(AccountClient.class);
        eventMetrics = Mockito.mock(EventMetrics.class);
        eventService = new EventService(eventRepository, accountClient, eventMetrics);
    }

    @Test
    void submitEvent_shouldReturnExistingEvent_whenDuplicateEventId() {
        EventRequest request = buildRequest("evt-123", "acct-1");
        EventRecord existing = buildRecord(request);
        when(eventRepository.findByEventId(request.getEventId())).thenReturn(Optional.of(existing));

        EventProcessingResult result = eventService.submitEvent(request, "trace-1");

        assertThat(result.created()).isFalse();
        assertThat(result.response()).isNotNull();
        assertThat(result.response().getEventId()).isEqualTo(request.getEventId());
        Mockito.verify(eventMetrics, times(1)).incrementDuplicate();
        Mockito.verify(accountClient, times(0)).postTransaction(any(), any(), any());
    }

    @Test
    void submitEvent_shouldPersistAndCallAccountService_whenValidNewEvent() {
        EventRequest request = buildRequest("evt-456", "acct-2");
        when(eventRepository.findByEventId(request.getEventId())).thenReturn(Optional.empty());
        when(eventRepository.save(any(EventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountClient.postTransaction(eq(request.getAccountId()), eq(request), eq("trace-2")))
                .thenReturn(buildResponse(request));

        EventProcessingResult result = eventService.submitEvent(request, "trace-2");

        assertThat(result.created()).isTrue();
        assertThat(result.response().getStatus()).isEqualTo(EventStatus.APPLIED.name());
        Mockito.verify(eventMetrics, times(1)).incrementCreated();
    }

    @Test
    void submitEvent_shouldReturnFailedAndThrow_whenAccountServiceUnavailable() {
        EventRequest request = buildRequest("evt-789", "acct-3");
        when(eventRepository.findByEventId(request.getEventId())).thenReturn(Optional.empty());
        when(eventRepository.save(any(EventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountClient.postTransaction(eq(request.getAccountId()), eq(request), eq("trace-3")))
                .thenThrow(new AccountServiceUnavailableException("Down"));

        assertThatThrownBy(() -> eventService.submitEvent(request, "trace-3"))
                .isInstanceOf(AccountServiceUnavailableException.class);

        ArgumentCaptor<EventRecord> savedCaptor = ArgumentCaptor.forClass(EventRecord.class);
        Mockito.verify(eventRepository, times(2)).save(savedCaptor.capture());
        List<EventRecord> savedRecords = savedCaptor.getAllValues();
        assertThat(savedRecords.get(1).getStatus()).isEqualTo(EventStatus.FAILED);
        Mockito.verify(eventMetrics, times(1)).incrementFailed();
    }

    @Test
    void submitEvent_shouldThrowBadRequest_whenInvalidEventType() {
        EventRequest request = buildRequest("evt-111", "acct-4");
        request.setType("INVALID");
        when(eventRepository.findByEventId(request.getEventId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.submitEvent(request, "trace-4"))
                .isInstanceOf(BadRequestException.class);
        Mockito.verify(eventRepository, times(0)).save(any(EventRecord.class));
    }

    @Test
    void getEventsByAccount_shouldReturnSortedEvents() {
        EventRecord earlier = buildRecord(buildRequest("evt-a", "acct-5"));
        earlier.setEventTimestamp(Instant.parse("2026-01-01T00:00:00Z"));
        EventRecord later = buildRecord(buildRequest("evt-b", "acct-5"));
        later.setEventTimestamp(Instant.parse("2026-01-02T00:00:00Z"));
        when(eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-5"))
                .thenReturn(List.of(earlier, later));

        var responses = eventService.getEventsByAccount("acct-5");

        assertThat(responses).extracting(EventResponse::getEventId).containsExactly("evt-a", "evt-b");
    }

    private EventRequest buildRequest(String eventId, String accountId) {
        return EventRequest.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type(EventType.CREDIT.name())
                .amount(BigDecimal.valueOf(10.00))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();
    }

    private EventRecord buildRecord(EventRequest request) {
        return EventRecord.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(EventType.CREDIT)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .status(EventStatus.APPLIED)
                .build();
    }

    private EventResponse buildResponse(EventRequest request) {
        return EventResponse.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .status(EventStatus.APPLIED.name())
                .build();
    }
}
