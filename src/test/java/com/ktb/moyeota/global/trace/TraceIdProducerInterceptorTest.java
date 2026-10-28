package com.ktb.moyeota.global.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceIdProducerInterceptorTest {

    private static final String TRACE_ID = "Root=1-67891233-abcdef012345678912345678";

    private final TraceIdProducerInterceptor interceptor = new TraceIdProducerInterceptor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("보내는 스레드의 추적 ID 를 메시지 헤더에 싣는다")
    void addsTraceIdFromMdc() {
        MDC.put(TraceId.LOG_KEY, TRACE_ID);

        ProducerRecord<Object, Object> sent = interceptor.onSend(new ProducerRecord<>("topic", "key", "value"));

        assertThat(headerValue(sent)).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("이미 실린 추적 ID 는 덮어쓰지 않는다")
    void keepsExistingHeader() {
        MDC.put(TraceId.LOG_KEY, TRACE_ID);
        ProducerRecord<Object, Object> record = new ProducerRecord<>("topic", "key", "value");
        record.headers().add(TraceId.KAFKA_HEADER, "Root=1-saved".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<Object, Object> sent = interceptor.onSend(record);

        assertThat(sent.headers().headers(TraceId.KAFKA_HEADER)).hasSize(1);
        assertThat(headerValue(sent)).isEqualTo("Root=1-saved");
    }

    @Test
    @DisplayName("추적 ID 가 없으면 헤더를 붙이지 않는다")
    void skipsWithoutTraceId() {
        ProducerRecord<Object, Object> sent = interceptor.onSend(new ProducerRecord<>("topic", "key", "value"));

        assertThat(sent.headers().lastHeader(TraceId.KAFKA_HEADER)).isNull();
    }

    private String headerValue(ProducerRecord<Object, Object> record) {
        Header header = record.headers().lastHeader(TraceId.KAFKA_HEADER);
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
