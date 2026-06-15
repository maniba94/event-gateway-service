package com.maniba.eventledger.gateway.util;

import org.slf4j.MDC;

public final class MdcUtils {

    public static final String TRACE_ID_KEY = "traceId";

    private MdcUtils() {
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
