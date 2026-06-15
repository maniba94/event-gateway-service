## **Project Context** 

This is the public-facing API service. It receives transaction events, validates them, ensures idempotency, stores event records in its own database, calls the internal `account-service` , propagates trace IDs, and handles downstream failures gracefully. 

Tech stack: 

• Java 17 • Spring Boot 3.5.15 • Gradle Groovy • Spring Web • Spring Reactive Web / WebClient • Spring Data JPA • H2 Database • Validation • Actuator • Resilience4j • Spring AOP 

Base package: `com.maniba.eventledger.gateway` 

Do not implement Account Service code in this project. Communicate with Account Service only through REST. 

## **Global Engineering Rules** 

Follow these rules throughout all phases: 

- Write clean, production-quality Spring Boot code. 
- Keep controller, service, client, repository, entity, dto, config, exception, filter, and util layers separated. 
- Do not over-engineer with Kafka, async messaging, or shared databases. 
- Use constructor injection only. 
- Use meaningful exception handling and HTTP status codes. 
- Ensure idempotency using `eventId` . 
- Store events in Gateway database before calling Account Service. 
- Propagate `X-Trace-Id` to Account Service. 
- Return `503 Service Unavailable` when Account Service is unavailable. 
- `GET /events` APIs should work even if Account Service is down. 
- Events returned by account must be sorted by `eventTimestamp` . 
- Add comments only where they clarify design decisions. 

## **Phase 1 — Project Structure** 

Create the following package structure: 

```
com.maniba.eventledger.gateway
├── controller
├── service
├── client
├── repository
├── entity
├── dto
├── config
├── exception
├── filter
├── metrics
└── util
```

Verify the application starts successfully on port `8080` . 

## **Phase 2 — Application Configuration** 

Create/update `application.yml` with: 

- server port `8080` • application name `event-gateway-service` • H2 in-memory database • JPA ddl auto update • H2 console enabled • Account Service base URL configurable through environment variable • Actuator health and metrics exposed • Resilience4j circuit breaker and retry configuration 

Use this property for Account Service: 
```
account:
    service:
        url:${ACCOUNT_SERVICE_URL:http://localhost:8081}

```

## **Phase 3 — Domain Model** 

Create `EventRecord` entity. 

Fields: 

```
id
eventId
accountId
type
amount
currency
eventTimestamp
status
metadataJson
createdAt
updatedAt
```

Constraints: 

- `eventId` must be unique 
- `eventId` , `accountId` , `type` , and `amount` are required 
- Use `BigDecimal` for amount 
- Use `Instant` for timestamps 

Use statuses: 

```
RECEIVED
APPLIED
FAILED
```

Use event types: 

```
CREDIT
DEBIT
```

## **Phase 4 — DTOs** 

Create request/response DTOs. 

`EventRequest` must include: 

```
eventId
accountId
type
amount
currency
eventTimestamp
metadata
```

Validation rules: 

- `eventId` required 
- `accountId` required
- `type` required 
- `amount` must be greater than 0 
- `currency` required 
- `eventTimestamp` required 

Create `EventResponse` with: 

```
eventId
accountId
type
amount
currency
eventTimestamp
status
```

## **Phase 5 — Repository** 

Create `EventRepository` . 

Required methods: 
```
Optional<EventRecord>findByEventId(StringeventId);
```

```
List<EventRecord>findByAccountIdOrderByEventTimestampAsc(StringaccountId);
```

## **Phase 6 — Trace ID Filter** 

Create a servlet filter that: 
1. Reads `X-Trace-Id` from incoming request. 
2. If missing, generates a UUID. 
3. Stores it in MDC as `traceId` 
4. Adds it to the response header. 
5. Clears MDC after request completion. 

This trace ID must be reused when calling Account Service. 

## **Phase 7 — Account Service Client** 

Create `AccountClient` using `WebClient` . 
It should call: 
```
POST /accounts/{accountId}/transactions
```
Rules: 
Use configured Account Service base URL. 

- Forward `X-Trace-Id` . 
- Use Resilience4j `@CircuitBreaker` . 
- Use Resilience4j `@Retry` . 
- On failure, throw `AccountServiceUnavailableException` . 
- Do not return raw WebClient exceptions to controller. 

## **Phase 8 — Event Service Logic** 

Create `EventService` . 
Implement `submitEvent(EventRequest request, String traceId)` . 
Flow: 
1. Check if eventId already exists.
2. If exists, return existing event response without calling Account Service.
3. Validate event type is CREDIT or DEBIT. 
4. Save event with status RECEIVED. 
5. Call Account Service. 
6. If Account Service call succeeds, update status to APPLIED.
7. If Account Service call fails, update status to FAILED and throw AccountServiceUnavailableException.
8. Return EventResponse.

Also implement: 
```
EventResponsegetEvent(StringeventId);
```
```
List<EventResponse>getEventsByAccount(StringaccountId);
```
`getEventsByAccount` must return events sorted by `eventTimestamp` ascending. 

## **Phase 9 — REST Controller** 

Create `EventController` . 

Expose: 

```
POST /events
GET /events/{eventId}
GET /events?account={accountId}
GET /health
```

## Behavior: 
- `POST /events` returns `201 Created` for newly applied event. 
- Duplicate event returns existing event and does not call Account Service again. 
- Invalid request returns `400 Bad Request` . 
- Account Service failure returns `503 Service Unavailable` . 
- `GET /events/{eventId}` returns `404` if not found. 
- `GET /events?account={accountId}` returns events sorted by event timestamp. 

## **Phase 10 — Exception Handling** 

Create a global exception handler. 
Handle: 
```
AccountServiceUnavailableException → 503
BadRequestException → 400
EventNotFoundException → 404
MethodArgumentNotValidException → 400
Generic Exception → 500
```
Error response should include: 
```
error
message
traceId
timestamp
```

Do not expose internal stack traces in API response. 

## **Phase 11 — Observability** 

Implement: 

1. Structured logs with trace ID. 
2. Actuator health endpoint. 
3. At least these metrics: 
4. events.created.count 
5. events.duplicate.count 
6. events.failed.count 
Use Micrometer counters. 
Add meaningful logs for: 
```
event received
duplicate event detected
```
```
account service call started
account service call succeeded
account service unavailable
event status updated
```
## **Phase 12 — Resilience4j** 
Configure: 
```
Retry:
- max attempts: 3
- wait duration: 500ms
```
```
Circuit breaker:
- sliding window size: 10
- minimum calls: 5
- failure rate threshold: 50
- wait duration in open state: 10 seconds
```
Ensure Account Service failure does not return 500. It must return 503. 
## **Phase 13 — Tests** 
Create unit and integration tests. 
Minimum required tests: 
```
POST valid event returns 201
duplicate event does not call Account Service again
invalid amount returns 400
invalid event type returns 400
missing required field returns 400
Account Service down returns 503
GET /events/{eventId} returns event
GET /events?account=accountId returns sorted events
Trace ID is propagated to Account Service
```

Use MockMvc for controller tests and WireMock or mocked `AccountClient` for downstream Account Service behavior. 

## **Phase 14 — Docker Support** 

Create `Dockerfile` . 
Use Java 17 image. 
The service must run with: 

```
dockerbuild-tevent-gateway-service.
dockerrun-p8080:8080event-gateway-service
```
Ensure Account Service URL can be overridden by: 

```
ACCOUNT_SERVICE_URL
```

## **Phase 15 — Final Quality Review** 

Before finishing, verify: 

1. Application starts on port 8080. 
2. H2 console works. 
3. `POST /events` works with Account Service running. 
4. Duplicate event does not double process. 
5. Account Service down returns 503. 
6. `GET /events` works even when Account Service is down. 
7. Trace ID appears in logs and response header. 
8. Events are returned sorted by `eventTimestamp` . 
9. No shared database or shared memory with Account Service. 
10. Code compiles and tests pass. 

Do not skip validation, exception handling, or tests. This project is evaluated for distributed system design, not just endpoint completion. 