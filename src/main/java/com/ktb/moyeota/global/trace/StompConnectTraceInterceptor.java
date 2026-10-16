package com.ktb.moyeota.global.trace;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StompConnectTraceInterceptor implements ChannelInterceptor {

    static final String SESSION_LOG_KEY = "websocket.session_id";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object traceId = attributes == null ? null : attributes.get(TraceId.LOG_KEY);
        try {
            if (traceId != null) {
                MDC.put(TraceId.LOG_KEY, traceId.toString());
            }
            MDC.put(SESSION_LOG_KEY, accessor.getSessionId());
            log.info("[STOMP_CONNECTED] userId={}", accessor.getUser() == null ? null : accessor.getUser().getName());
        } finally {
            MDC.remove(TraceId.LOG_KEY);
            MDC.remove(SESSION_LOG_KEY);
        }
        return message;
    }
}
