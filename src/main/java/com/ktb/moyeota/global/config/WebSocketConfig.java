package com.ktb.moyeota.global.config;

import com.ktb.moyeota.global.security.StompAuthChannelInterceptor;
import com.ktb.moyeota.global.trace.StompConnectTraceInterceptor;
import com.ktb.moyeota.global.trace.TraceIdHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ENDPOINT = "/api/wss";
    private static final String APPLICATION_DESTINATION_PREFIX = "/pub";
    private static final String BROKER_PREFIX = "/sub";

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompConnectTraceInterceptor stompConnectTraceInterceptor;
    private final TraceIdHandshakeInterceptor traceIdHandshakeInterceptor;
    private final CorsProperties corsProperties;
    private final Environment environment;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        if (!ServerRole.from(environment.getProperty(ServerRole.ENV_NAME)).acceptsWebSocket()) {
            return;
        }
        registry.addEndpoint(ENDPOINT)
                .addInterceptors(traceIdHandshakeInterceptor)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        registry.enableSimpleBroker(BROKER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor, stompConnectTraceInterceptor);
    }
}
