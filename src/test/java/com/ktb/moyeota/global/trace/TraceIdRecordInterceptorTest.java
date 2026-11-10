package com.ktb.moyeota.global.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceIdRecordInterceptorTest {

    private static final String TRACE_ID = "Root=1-67891233-abcdef012345678912345678";

    private final TraceIdRecordInterceptor interceptor = new TraceIdRecordInterceptor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("메시지 헤더의 추적 ID 를 처리 중 로그에 넣고, 처리가 끝나면 뺀다")
    void putsTraceIdWhileHandlingRecord() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, "key", "value");
        record.headers().add(TraceId.KAFKA_HEADER, TRACE_ID.getBytes(StandardCharsets.UTF_8));

        interceptor.intercept(record, null);
        String duringHandling = MDC.get(TraceId.LOG_KEY);
        interceptor.afterRecord(record, null);

        assertThat(duringHandling).isEqualTo(TRACE_ID);
        assertThat(MDC.get(TraceId.LOG_KEY)).isNull();
    }

    @Test
    @DisplayName("헤더에 추적 ID 가 없으면 로그에 넣지 않는다")
    void skipsWithoutHeader() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("topic", 0, 0L, "key", "value");

        interceptor.intercept(record, null);

        assertThat(MDC.get(TraceId.LOG_KEY)).isNull();
    }
}
