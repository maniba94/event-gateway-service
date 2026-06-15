package com.maniba.eventledger.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {

    private String eventId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private String status;
}
