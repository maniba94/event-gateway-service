# Event Gateway Service

A Spring Boot API gateway for transaction events. It validates event payloads, stores events in a local H2 database, ensures idempotency by `eventId`, and forwards successful transactions to an external Account Service.

## Features

- Java 17, Spring Boot 3.5.15
- REST API for event ingestion and retrieval
- H2 in-memory database for gateway event store
- Idempotency by `eventId`
- `X-Trace-Id` propagation for distributed tracing
- Resilience4j retry and circuit breaker for Account Service calls
- Metrics and Actuator health
- Docker-ready deployment

## Configuration

Default values are set in `src/main/resources/application.yaml`.

- `ACCOUNT_SERVICE_URL` — override the Account Service base URL

Example:

```bash
export ACCOUNT_SERVICE_URL=http://localhost:8081
```

## Build & Run

### Local

```bash
./gradlew clean build
java -jar build/libs/event-gateway-service-0.0.1-SNAPSHOT.jar
```

### Tests

```bash
./gradlew test
```

## Docker

Build the image:

```bash
docker build -t event-gateway-service .
```

Run the container:

```bash
docker run -p 8080:8080 -e ACCOUNT_SERVICE_URL=http://host.docker.internal:8081 event-gateway-service
```

## Endpoints

- `POST /events`
- `GET /events/{eventId}`
- `GET /events?account={accountId}`
- `GET /health`
- `GET /h2-console` (H2 console)
- `GET /actuator/health`
- `GET /actuator/metrics`

## Notes

- The gateway persists events locally and does not share a database with the Account Service.
- If the Account Service is unavailable, the gateway updates event status to `FAILED` and returns `503 Service Unavailable`.
- Duplicate events are detected by `eventId` and are not reprocessed.
