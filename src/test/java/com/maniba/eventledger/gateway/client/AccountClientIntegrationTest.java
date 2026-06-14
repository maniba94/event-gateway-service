package com.maniba.eventledger.gateway.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maniba.eventledger.gateway.config.AccountServiceProperties;
import com.maniba.eventledger.gateway.dto.EventRequest;
import com.maniba.eventledger.gateway.dto.EventResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountClientIntegrationTest {

    private static WireMockServer wireMockServer;
    private static AccountClient accountClient;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        AccountServiceProperties properties = new AccountServiceProperties();
        properties.setUrl("http://localhost:" + wireMockServer.port());
        WebClient webClient = WebClient.builder().baseUrl(properties.getUrl()).build();
        accountClient = new AccountClient(webClient);
    }

    @AfterAll
    static void afterAll() {
    if (wireMockServer != null) {
        wireMockServer.stop();
    }
}

    @Test
    void postTransaction_shouldPropagateTraceIdAndReturnResponse() throws Exception {
        EventRequest request = buildRequest("evt-200", "acct-200");
        EventResponse response = buildResponse(request, "APPLIED");

        wireMockServer.stubFor(post(urlPathEqualTo("/accounts/acct-200/transactions"))
                .withHeader("X-Trace-Id", WireMock.matching("^trace-.*$"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(response))));

        EventResponse result = accountClient.postTransaction("acct-200", request, "trace-200");

        assertThat(result.getEventId()).isEqualTo(request.getEventId());
    }

    @Test
    void postTransaction_shouldThrowWhenServiceUnavailable() {
        EventRequest request = buildRequest("evt-201", "acct-201");

        wireMockServer.stubFor(post(urlPathEqualTo("/accounts/acct-201/transactions"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(RuntimeException.class, () -> accountClient.postTransaction("acct-201", request, "trace-201"));
    }

    private EventRequest buildRequest(String eventId, String accountId) {
        return EventRequest.builder()
                .eventId(eventId)
                .accountId(accountId)
                .type("CREDIT")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadata(Map.of("key", "value"))
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
