package com.ktb.moyeota.global.config;

import com.ktb.moyeota.global.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
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
    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        registry.enableSimpleBroker(BROKER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
