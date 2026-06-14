package com.maniba.eventledger.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maniba.eventledger.gateway.dto.EventRequest;
import com.maniba.eventledger.gateway.dto.EventResponse;
import com.maniba.eventledger.gateway.service.EventProcessingResult;
import com.maniba.eventledger.gateway.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @BeforeEach
    void setUp() {
        Mockito.reset(eventService);
    }

    @Test
    void postEvents_shouldReturnCreatedForNewEvent() throws Exception {
        EventRequest request = buildRequest("evt-100", "acct-100");
        EventResponse response = buildResponse(request, "APPLIED");
        Mockito.when(eventService.submitEvent(any(), anyString()))
                .thenReturn(new EventProcessingResult(response, true));

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/events/evt-100"));
    }

    @Test
    void postEvents_shouldReturnBadRequestForMissingRequiredField() throws Exception {
        String payload = "{\"eventId\":\"evt-101\"}";

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void postEvents_shouldReturnBadRequestForInvalidAmount() throws Exception {
        String payload = "{\"eventId\":\"evt-102\",\"accountId\":\"acct-102\",\"type\":\"CREDIT\",\"amount\":0,\"currency\":\"USD\",\"eventTimestamp\":\"2026-01-01T00:00:00Z\"}";

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void postEvents_shouldReturnBadRequestForInvalidEventType() throws Exception {
        EventRequest request = buildRequest("evt-103", "acct-103");
        request.setType("INVALID_TYPE");
        Mockito.when(eventService.submitEvent(any(), anyString()))
                .thenThrow(new com.maniba.eventledger.gateway.exception.BadRequestException("Invalid event type"));

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void postEvents_shouldReturnOkForDuplicateEvent() throws Exception {
        EventRequest request = buildRequest("evt-101", "acct-101");
        EventResponse response = buildResponse(request, "APPLIED");
        Mockito.when(eventService.submitEvent(any(), anyString()))
                .thenReturn(new EventProcessingResult(response, false));

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getEvent_shouldReturnOk() throws Exception {
        EventResponse response = buildResponse(buildRequest("evt-102", "acct-102"), "APPLIED");
        Mockito.when(eventService.getEvent("evt-102")).thenReturn(response);

        mockMvc.perform(get("/events/evt-102"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    private EventRequest buildRequest(String eventId, String accountId) {
        return EventRequest.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type("CREDIT")
                .amount(BigDecimal.valueOf(12.34))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();
    }

    private EventResponse buildResponse(EventRequest request, String status) {
        return EventResponse.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .status(status)
                .build();
    }
}
