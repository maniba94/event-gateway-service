package com.maniba.eventledger.gateway.controller;

import com.maniba.eventledger.gateway.dto.EventRequest;
import com.maniba.eventledger.gateway.dto.EventResponse;
import com.maniba.eventledger.gateway.service.EventProcessingResult;
import com.maniba.eventledger.gateway.service.EventService;
import com.maniba.eventledger.gateway.util.MdcUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(
            @Valid @RequestBody EventRequest request,
            @RequestHeader(value = TRACE_ID_HEADER, required = false) String traceIdHeader) {

        String traceId = traceIdHeader != null ? traceIdHeader : MdcUtils.getTraceId();
        log.info("submitEvent requested eventId={} accountId={} traceId={}", request.getEventId(), request.getAccountId(), traceId);

        EventProcessingResult result = eventService.submitEvent(request, traceId);
        URI location = URI.create(String.format("/events/%s", result.response().getEventId()));

        if (result.created()) {
            return ResponseEntity.created(location).body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String eventId) {
        log.info("getEvent requested eventId={}", eventId);
        EventResponse response = eventService.getEvent(eventId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEventsByAccount(@RequestParam(name = "account") String accountId) {
        log.info("getEventsByAccount requested accountId={}", accountId);
        List<EventResponse> responses = eventService.getEventsByAccount(accountId);
        return ResponseEntity.ok(responses);
    }
}
