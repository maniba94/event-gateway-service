package com.maniba.eventledger.gateway.client;

import com.maniba.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.maniba.eventledger.gateway.exception.BadRequestException;
import com.maniba.eventledger.gateway.dto.EventRequest;
import com.maniba.eventledger.gateway.dto.EventResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);
    private final WebClient webClient;

    public AccountClient(WebClient accountServiceWebClient) {
        this.webClient = accountServiceWebClient;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "accountServiceFallback")
    @Retry(name = "accountService")
    public EventResponse postTransaction(String accountId, EventRequest request, String traceId) {
        log.info("account service call started for accountId={}", accountId);
        try {
            return webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/accounts/{accountId}/transactions").build(accountId))
                    .header("X-Trace-Id", traceId)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EventResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("account service returned error: {}", ex.getStatusCode(), ex);
            if (ex.getStatusCode().is4xxClientError()) {
                throw new BadRequestException("Account service rejected the request");
            }
            throw new AccountServiceUnavailableException("Account service unavailable", ex);
        } catch (Exception ex) {
            log.error("account service unavailable", ex);
            throw new AccountServiceUnavailableException("Account service unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    private EventResponse accountServiceFallback(String accountId, EventRequest request, String traceId, Throwable throwable) {
        throw new AccountServiceUnavailableException("Account service unavailable", throwable);
    }
}
