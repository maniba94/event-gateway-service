package com.maniba.eventledger.gateway.exception;

import lombok.Getter;

import java.time.Instant;

@Getter
public class ApiError {

    private final String error;
    private final String message;
    private final String traceId;
    private final Instant timestamp;

    public ApiError(String error, String message, String traceId, Instant timestamp) {
        this.error = error;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

}
