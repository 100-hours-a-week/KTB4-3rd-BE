package com.ktb.moyeota.global.websocket.relay;

import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnServerRole({ServerRole.WEBSOCKET, ServerRole.ALL})
public class WsRelaySubscriberConfig {

    public static final String LISTENER_EXECUTOR = "wsRelayListenerExecutor";

    @Bean(name = LISTENER_EXECUTOR)
    public ThreadPoolTaskExecutor wsRelayListenerExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(1);
        taskExecutor.setThreadNamePrefix("ws-relay-");
        return taskExecutor;
    }

    @Bean
    public RedisMessageListenerContainer wsRelayListenerContainer(
            RedisConnectionFactory connectionFactory,
            WsRelayListener wsRelayListener,
            RelayChannel relayChannel,
            @Qualifier(LISTENER_EXECUTOR) ThreadPoolTaskExecutor listenerExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(listenerExecutor);
        container.addMessageListener(wsRelayListener, new PatternTopic(relayChannel.pattern()));
        container.setAutoStartup(false);
        return container;
    }

    @Bean
    public WsRelaySubscriptionStarter wsRelaySubscriptionStarter(
            RedisConnectionFactory connectionFactory,
            RedisMessageListenerContainer wsRelayListenerContainer) {
        return new WsRelaySubscriptionStarter(connectionFactory, wsRelayListenerContainer);
    }
}
