# Event Gateway Service

A Spring Boot API gateway for transaction events. It validates event payloads, stores events in a local H2 database, ensures idempotency by `eventId`, and forwards successful transactions to an external Account Service.

## Architecture Overview

The project is an API gateway that sits between clients and an external Account Service.

- `event-gateway-service`
  - Receives and validates incoming transaction events via `POST /events`.
  - Persists each event in a local H2 database before calling the Account Service.
  - Ensures idempotency on `eventId` so duplicate requests do not create duplicate transactions.
  - Propagates `X-Trace-Id` to the Account Service for distributed tracing.
  - Exposes read APIs to retrieve events by `eventId` or `accountId`.

- `Account Service`
  - External service responsible for accepting transactions at `/accounts/{accountId}/transactions`.
  - Not included in this repository; the gateway calls it using the configured `ACCOUNT_SERVICE_URL`.

The gateway writes events locally first, then forwards the business request to the Account Service. This design keeps event persistence independent from the downstream service and allows the gateway to return meaningful status if the downstream service is unavailable.

## Setup Instructions

### Prerequisites

- Java 17+
- Gradle wrapper included in the repo (`./gradlew` / `gradlew.bat`)
- Docker and Docker Compose installed if you want containerized execution
- An Account Service available at the configured `ACCOUNT_SERVICE_URL` (default: `http://localhost:8081`)

### Install dependencies

Dependencies are downloaded automatically by the Gradle wrapper.

From the project root:

```bash
./gradlew clean build --refresh-dependencies
```

If you are on Windows, use:

```powershell
.\gradlew.bat clean build --refresh-dependencies
```

## How to Start Both Services

### Manual Start

1. Start the Account Service separately at `http://localhost:8081`.
2. Start the gateway service:

```bash
./gradlew bootRun
```

or:

```bash
java -jar build/libs/event-gateway-service-0.0.1-SNAPSHOT.jar
```

3. Override the target Account Service URL if needed:

```bash
export ACCOUNT_SERVICE_URL=http://localhost:8081
./gradlew bootRun
```

On Windows PowerShell:

```powershell
$env:ACCOUNT_SERVICE_URL = 'http://localhost:8081'
.\gradlew.bat bootRun
```

### Docker

Build the gateway image:

```bash
docker build -t event-gateway-service .
```

Run the gateway container:

```bash
docker run -p 8080:8080 -e ACCOUNT_SERVICE_URL=http://host.docker.internal:8081 event-gateway-service
```

> Note: This repository does not include a Docker Compose file, so start the Account Service independently or add your own Compose configuration.

## How to Run Tests

Run the full test suite with Gradle:

```bash
./gradlew test
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
```

## Resiliency Pattern Explanation

This gateway uses Resilience4j with a retry policy and circuit breaker for calls to the Account Service.

- Retry
  - Attempts a failed Account Service call up to 3 times with a short wait between attempts.
  - Helps recover from transient network or service errors.

- Circuit Breaker
  - Opens when the downstream service fails repeatedly, preventing additional requests from overwhelming it.
  - After a configured wait period, it allows a small number of trial requests to verify recovery.

This combination improves availability and protects the gateway from cascading failures when the Account Service becomes unstable.

## Configuration

Default values are set in `src/main/resources/application.yaml`.

- `ACCOUNT_SERVICE_URL` — override the Account Service base URL

Example:

```bash
export ACCOUNT_SERVICE_URL=http://localhost:8081
```

## Endpoints

- `POST /events`
- `GET /events/{eventId}`
- `GET /events?account={accountId}`
- `GET /health`
- `GET /h2-console`
- `GET /actuator/health`
- `GET /actuator/metrics`

## Notes

- The gateway persists events locally and does not share a database with the Account Service.
- If the Account Service is unavailable, the gateway throws `503 Service Unavailable` and marks the event as failed.
- Duplicate events are detected by `eventId` and are not reprocessed.
