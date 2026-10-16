package com.ktb.moyeota.global.websocket.relay;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
public class WsRelaySubscriptionStarter {

    private static final Duration INITIAL_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_DELAY = Duration.ofSeconds(60);

    private final RedisConnectionFactory connectionFactory;
    private final RedisMessageListenerContainer container;
    private final ScheduledExecutorService executor;

    public WsRelaySubscriptionStarter(RedisConnectionFactory connectionFactory,
                                      RedisMessageListenerContainer container) {
        this.connectionFactory = connectionFactory;
        this.container = container;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("ws-relay-subscribe").daemon().factory());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubscription() {
        executor.execute(() -> tryStart(0));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    boolean attempt() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            return false;
        }
        try {
            container.start();
            return true;
        } catch (RuntimeException e) {
            container.stop();
            return false;
        }
    }

    static Duration delayAfter(int failures) {
        if (failures >= 4) {
            return MAX_DELAY;
        }
        Duration delay = INITIAL_DELAY.multipliedBy(1L << failures);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    private void tryStart(int failures) {
        if (attempt()) {
            log.info("[WS_RELAY_SUBSCRIBED] Redis 구독을 시작했습니다.");
            executor.shutdown();
            return;
        }
        Duration delay = delayAfter(failures);
        log.warn("[WS_RELAY_SUBSCRIBE_WAITING] Redis에 연결하지 못해 {}초 뒤 다시 구독합니다.", delay.toSeconds());
        executor.schedule(() -> tryStart(failures + 1), delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
