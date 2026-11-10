package com.ktb.moyeota.global.trace;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompConnectTraceInterceptorTest {

    private static final String TRACE_ID = "Root=1-67891233-abcdef012345678912345678";
    private static final String SESSION_ID = "stomp-session-1";

    private final StompConnectTraceInterceptor interceptor = new StompConnectTraceInterceptor();
    private final Logger logger = (Logger) LoggerFactory.getLogger(StompConnectTraceInterceptor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("CONNECT 때 추적 ID와 STOMP 세션 ID를 한 줄 남긴다")
    void logsTraceIdAndSessionIdOnConnect() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TraceId.LOG_KEY, TRACE_ID);

        interceptor.preSend(message(StompCommand.CONNECT, attributes), null);

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getMessage()).startsWith("[STOMP_CONNECTED]");
            assertThat(event.getMDCPropertyMap())
                    .containsEntry(TraceId.LOG_KEY, TRACE_ID)
                    .containsEntry(StompConnectTraceInterceptor.SESSION_LOG_KEY, SESSION_ID);
        });
        assertThat(MDC.get(TraceId.LOG_KEY)).isNull();
        assertThat(MDC.get(StompConnectTraceInterceptor.SESSION_LOG_KEY)).isNull();
    }

    @Test
    @DisplayName("추적 ID가 없어도 세션 ID는 남긴다")
    void logsSessionIdWithoutTraceId() {
        interceptor.preSend(message(StompCommand.CONNECT, new HashMap<>()), null);

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getMDCPropertyMap())
                .doesNotContainKey(TraceId.LOG_KEY)
                .containsEntry(StompConnectTraceInterceptor.SESSION_LOG_KEY, SESSION_ID));
    }

    @Test
    @DisplayName("CONNECT가 아닌 프레임은 남기지 않는다")
    void ignoresOtherFrames() {
        interceptor.preSend(message(StompCommand.SEND, new HashMap<>()), null);

        assertThat(appender.list).isEmpty();
    }

    private static Message<byte[]> message(StompCommand command, Map<String, Object> attributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(SESSION_ID);
        accessor.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
