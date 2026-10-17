package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import({RedisTestContainerConfig.class, RelayChannel.class, RedisWsRelayPublisher.class, WsRelayListener.class,
        WsRelaySubscriberConfig.class, WsRelaySubscriberIntegrationTest.Config.class})
@EnableConfigurationProperties(RelayProperties.class)
class WsRelaySubscriberIntegrationTest {

    private static final String PROBE_CHANNEL = "moyeota:local:ws:v1:probe";

    record Payload(Long messageId, String content) {
    }

    record Handled(Long roomId, RelayMessage message) {
    }

    @Autowired
    private WsRelayPublisher publisher;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private BlockingQueue<Handled> handled;

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    @DisplayName("발행한 채팅방 메시지가 Redis 구독을 거쳐 채팅방 처리기에 도착한다")
    void deliversRoomMessageToHandler() throws Exception {
        awaitSubscription();

        publisher.toRoom(7L, "/sub/chat/7", new Payload(900L, "출발지 앞이에요"));

        Handled result = handled.poll(3, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.roomId()).isEqualTo(7L);
        assertThat(result.message().destination()).isEqualTo("/sub/chat/7");
        assertThat(result.message().payload())
                .isEqualTo(Map.of("message_id", 900, "content", "출발지 앞이에요"));
    }

    @Test
    @DisplayName("컨텍스트가 멈췄다 다시 시작되면 Redis 구독도 다시 시작한다")
    void resubscribesAfterRestart() throws Exception {
        awaitSubscription();

        context.pause();
        context.restart();

        awaitSubscription();
    }

    private void awaitSubscription() throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (System.currentTimeMillis() < deadline) {
            Long receivers = redisTemplate.convertAndSend(PROBE_CHANNEL, "probe");
            if (receivers != null && receivers > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Redis 구독이 준비되지 않았습니다.");
    }

    @TestConfiguration
    static class Config {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        BlockingQueue<Handled> handled() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        RoomRelayHandler roomRelayHandler(BlockingQueue<Handled> handled) {
            return (roomId, message) -> handled.add(new Handled(roomId, message));
        }
    }
}
