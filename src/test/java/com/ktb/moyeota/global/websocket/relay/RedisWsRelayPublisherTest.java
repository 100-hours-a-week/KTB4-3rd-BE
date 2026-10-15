package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RedisWsRelayPublisherTest {

    record Payload(Long messageId, String content) {
    }

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisWsRelayPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RedisWsRelayPublisher(
                redisTemplate, jsonMapper, new RelayChannel(new RelayProperties("local")), meterRegistry);
    }

    @Test
    @DisplayName("채팅방 채널로 목적지와 내용을 snake_case JSON으로 발행한다")
    void publishesToRoomChannel() {
        publisher.toRoom(7L, "/sub/chat/7", new Payload(900L, "출발지 앞이에요"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("moyeota:local:ws:v1:room:7"), body.capture());
        assertThat(jsonMapper.readTree(body.getValue())).isEqualTo(jsonMapper.readTree(
                "{\"destination\":\"/sub/chat/7\",\"payload\":{\"message_id\":900,\"content\":\"출발지 앞이에요\"}}"));
        assertThat(count("room", "success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("사용자 채널로 목적지와 내용을 발행한다")
    void publishesToUserChannel() {
        publisher.toUser(12L, "/user/sub/settlement", new Payload(77L, "분석 완료"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("moyeota:local:ws:v1:user:12"), body.capture());
        assertThat(jsonMapper.readTree(body.getValue())).isEqualTo(jsonMapper.readTree(
                "{\"destination\":\"/user/sub/settlement\",\"payload\":{\"message_id\":77,\"content\":\"분석 완료\"}}"));
        assertThat(count("user", "success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis 발행이 실패해도 예외를 던지지 않고 실패 횟수를 센다")
    void countsFailureWithoutThrowing() {
        given(redisTemplate.convertAndSend(anyString(), anyString()))
                .willThrow(new RedisConnectionFailureException("Redis에 연결할 수 없습니다."));

        assertThatCode(() -> publisher.toRoom(7L, "/sub/chat/7", new Payload(900L, "출발지 앞이에요")))
                .doesNotThrowAnyException();
        assertThat(count("room", "failure")).isEqualTo(1.0);
    }

    private double count(String target, String result) {
        return meterRegistry.counter("ws.relay.publish", "target", target, "result", result).count();
    }
}
