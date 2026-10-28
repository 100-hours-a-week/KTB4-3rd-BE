package com.ktb.moyeota.global.trace;

public final class TraceId {

    public static final String HEADER = "X-Amzn-Trace-Id";
    public static final String LOG_KEY = "trace.id";
    public static final String SENTRY_TAG = "aws_trace_id";
    public static final String KAFKA_HEADER = "trace_id";

    private TraceId() {
    }
}
