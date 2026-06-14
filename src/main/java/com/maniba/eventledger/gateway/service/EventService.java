package com.maniba.eventledger.gateway.service;

import com.maniba.eventledger.gateway.client.AccountClient;
import com.maniba.eventledger.gateway.dto.EventRequest;
import com.maniba.eventledger.gateway.dto.EventResponse;
import com.maniba.eventledger.gateway.dto.MetadataSerializer;
import com.maniba.eventledger.gateway.entity.EventRecord;
import com.maniba.eventledger.gateway.entity.EventStatus;
import com.maniba.eventledger.gateway.entity.EventType;
import com.maniba.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.maniba.eventledger.gateway.exception.BadRequestException;
import com.maniba.eventledger.gateway.exception.EventNotFoundException;
import com.maniba.eventledger.gateway.metrics.EventMetrics;
import com.maniba.eventledger.gateway.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final AccountClient accountClient;
    private final EventMetrics eventMetrics;

    public EventService(EventRepository eventRepository, AccountClient accountClient, EventMetrics eventMetrics) {
        this.eventRepository = eventRepository;
        this.accountClient = accountClient;
        this.eventMetrics = eventMetrics;
    }

    @Transactional
    public EventProcessingResult submitEvent(EventRequest request, String traceId) {
        log.info("event received eventId={} accountId={}", request.getEventId(), request.getAccountId());

        return eventRepository.findByEventId(request.getEventId())
                .map(existing -> {
                    log.info("duplicate event detected eventId={}", request.getEventId());
                    eventMetrics.incrementDuplicate();
                    return new EventProcessingResult(mapToResponse(existing), false);
                })
                .orElseGet(() -> {
                    EventResponse response = processNewEvent(request, traceId);
                    eventMetrics.incrementCreated();
                    return new EventProcessingResult(response, true);
                });
    }

    public EventResponse getEvent(String eventId) {
        EventRecord record = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found for eventId: " + eventId));
        return mapToResponse(record);
    }

    public List<EventResponse> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private EventResponse processNewEvent(EventRequest request, String traceId) {
        EventType eventType = parseEventType(request.getType());

        EventRecord record = EventRecord.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(eventType)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .status(EventStatus.RECEIVED)
                .metadataJson(MetadataSerializer.toJson(request.getMetadata()))
                .build();

        record = eventRepository.save(record);
        log.info("event status updated eventId={} status={}", record.getEventId(), record.getStatus());

        try {
            accountClient.postTransaction(record.getAccountId(), request, traceId);
            record.setStatus(EventStatus.APPLIED);
            record = eventRepository.save(record);
            log.info("account service call succeeded eventId={}", record.getEventId());
            log.info("event status updated eventId={} status={}", record.getEventId(), record.getStatus());
            return mapToResponse(record);
        } catch (AccountServiceUnavailableException ex) {
            record.setStatus(EventStatus.FAILED);
            eventRepository.save(record);
            eventMetrics.incrementFailed();
            log.error("account service unavailable eventId={}", record.getEventId());
            throw ex;
        }
    }

    private EventType parseEventType(String typeValue) {
        if (typeValue == null || typeValue.isBlank()) {
            throw new BadRequestException("type is required");
        }
        try {
            return EventType.valueOf(typeValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid event type: " + typeValue + ". Allowed values are CREDIT or DEBIT.");
        }
    }

    private EventResponse mapToResponse(EventRecord record) {
        return EventResponse.builder()
                .eventId(record.getEventId())
                .accountId(record.getAccountId())
                .type(record.getType().name())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .eventTimestamp(record.getEventTimestamp())
                .status(record.getStatus().name())
                .build();
    }
}
