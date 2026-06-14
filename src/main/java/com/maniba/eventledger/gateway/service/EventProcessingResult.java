package com.maniba.eventledger.gateway.service;

import com.maniba.eventledger.gateway.dto.EventResponse;

public record EventProcessingResult(EventResponse response, boolean created) {
}
