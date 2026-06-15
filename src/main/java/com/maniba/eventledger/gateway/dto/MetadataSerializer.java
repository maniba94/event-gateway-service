package com.maniba.eventledger.gateway.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class MetadataSerializer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MetadataSerializer() {
    }

    public static String toJson(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize metadata", e);
        }
    }
}
