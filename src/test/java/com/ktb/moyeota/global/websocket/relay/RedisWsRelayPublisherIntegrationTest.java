package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import({RedisTestContainerConfig.class, RedisWsRelayPublisher.class, RelayChannel.class,
        RedisWsRelayPublisherIntegrationTest.Config.class})
@EnableConfigurationProperties(RelayProperties.class)
class RedisWsRelayPublisherIntegrationTest {

    record Payload(Long messageId, String content) {
    }

    record Received(String channel, String body) {
    }

    @Autowired
    private RedisWsRelayPublisher publisher;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private JsonMapper jsonMapper;

    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private RedisMessageListenerContainer container;

    @BeforeEach
    void subscribe() {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> received.add(new Received(
                new String(message.getChannel(), StandardCharsets.UTF_8),
                new String(message.getBody(), StandardCharsets.UTF_8))),
                new PatternTopic("moyeota:local:ws:v1:*"));
        container.afterPropertiesSet();
        container.start();
    }

    @AfterEach
    void unsubscribe() throws Exception {
        container.destroy();
    }

    @Test
    @DisplayName("발행한 메시지가 실제 Redis를 거쳐 패턴 구독자에게 채널 이름과 함께 도착한다")
    void deliversThroughRedis() throws Exception {
        publisher.toRoom(7L, "/sub/chat/7", new Payload(900L, "출발지 앞이에요"));

        Received message = received.poll(3, TimeUnit.SECONDS);

        assertThat(message).isNotNull();
        assertThat(message.channel()).isEqualTo("moyeota:local:ws:v1:room:7");
        assertThat(jsonMapper.readTree(message.body())).isEqualTo(jsonMapper.readTree(
                "{\"destination\":\"/sub/chat/7\",\"payload\":{\"message_id\":900,\"content\":\"출발지 앞이에요\"}}"));
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
    }
}
