# Event Gateway Service Functionality Test

## Overview
This document verifies the core behavior of the Event Gateway Service. It covers API endpoints, request examples, expected responses, and the recommended testing flow for each functional area.

## Test Environment
- Service base URL: `http://localhost:8080`
- Account Service base URL: configured via `ACCOUNT_SERVICE_URL` or defaults to `http://localhost:8081`
- H2 database available in-memory
- Actuator enabled
- Trace ID header: `X-Trace-Id`

## Endpoints

### POST /events
Create or submit a transaction event.

- Request URL: `POST http://localhost:8080/events`
- Required headers:
  - `Content-Type: application/json`
  - `X-Trace-Id` (optional, generated if missing)
- Request body:
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-06-14T12:00:00Z",
  "metadata": {
    "source": "test"
  }
}
```

### GET /events/{eventId}
Retrieve a single event by its unique ID.

- Request URL: `GET http://localhost:8080/events/evt-001`

### GET /events?account={accountId}
Retrieve all events for the specified account, sorted by `eventTimestamp` ascending.

- Request URL: `GET http://localhost:8080/events?account=acct-123`

### GET /health
Check service health.

- Request URL: `GET http://localhost:8080/health`

## Testing Flow

### 1. Verify service startup
- Start the application and confirm it listens on `http://localhost:8080`.
- Confirm `/health` returns `200 OK`.

### 2. Test valid event submission
- Send `POST /events` with a valid event payload.
- Expected behavior:
  - `201 Created` when the event is new and successfully applied.
  - Response body contains the event fields including `status: APPLIED`.
  - Response `Location` header points to `/events/{eventId}`.
  - `X-Trace-Id` is preserved or generated and propagated to downstream Account Service.

### 3. Test duplicate event handling
- Submit the same `eventId` again with the same payload.
- Expected behavior:
  - Service returns `200 OK` or an existing event response without calling Account Service again.
  - The stored event is not duplicated.
  - Metrics or logs should record a duplicate event detection.

### 4. Test invalid payloads
#### Invalid amount
- Request body with `"amount": 0` or negative value.
- Expected response: `400 Bad Request`.

#### Invalid event type
- Request body with `"type": "TRANSFER"`.
- Expected response: `400 Bad Request`.

#### Missing required field
- Remove `accountId`, `eventId`, or `eventTimestamp`.
- Expected response: `400 Bad Request`.

### 5. Test Account Service unavailability
- Configure Account Service to be unavailable or return errors.
- Submit a valid event payload.
- Expected behavior:
  - Service stores the event with status `FAILED`.
  - Controller returns `503 Service Unavailable`.
  - `GET /events` and `GET /events/{eventId}` should still retrieve stored event data.

### 6. Test `GET /events/{eventId}`
- Request an existing event by ID.
- Expected response: `200 OK` with the correct event JSON.
- For a missing event ID, expected response: `404 Not Found`.

### 7. Test `GET /events?account={accountId}` sorting
- Submit multiple events for the same account with different `eventTimestamp` values.
- Request `GET /events?account=acct-123`.
- Expected response: events sorted by `eventTimestamp` ascending.

### 8. Test trace ID propagation
- Send a request to `POST /events` with header `X-Trace-Id: trace-abc-123`.
- Verify the same header is sent to Account Service.
- Verify the response or logs include the trace ID.

### 9. Health endpoint and actuator
- Confirm `GET /health` returns `200 OK`.
- Confirm the service still returns events even if Account Service is down.

## Sample Test Cases

### Positive case
1. POST `/events`
2. GET `/events/evt-001`
3. GET `/events?account=acct-123`

### Negative case
1. POST `/events` with invalid amount
2. Validate `400 Bad Request`

### Downstream failure case
1. Stop Account Service or make it return `500`
2. POST `/events`
3. Validate `503 Service Unavailable`
4. GET `/events/evt-001` returns stored event

## Notes
- Use the configured `ACCOUNT_SERVICE_URL` to point to the mock or real Account Service during tests.
- Ensure the event store is isolated; Account Service must only be called by the gateway and not share databases.
- Confirm `eventTimestamp` ordering in `GET /events` is always ascending.
