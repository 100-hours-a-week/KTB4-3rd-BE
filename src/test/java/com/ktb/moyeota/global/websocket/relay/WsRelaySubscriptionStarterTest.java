package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@ExtendWith(MockitoExtension.class)
class WsRelaySubscriptionStarterTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Mock
    private RedisMessageListenerContainer container;

    private WsRelaySubscriptionStarter starter;

    @BeforeEach
    void setUp() {
        starter = new WsRelaySubscriptionStarter(connectionFactory, container);
    }

    @AfterEach
    void tearDown() {
        starter.shutdown();
    }

    @Test
    @DisplayName("Redis에 연결되면 구독을 시작한다")
    void startsWhenRedisReachable() {
        given(connectionFactory.getConnection()).willReturn(connection);

        assertThat(starter.attempt()).isTrue();

        verify(container).start();
    }

    @Test
    @DisplayName("Redis에 연결되지 않으면 구독을 시작하지 않고 실패로 알린다")
    void waitsWhenRedisUnreachable() {
        given(connectionFactory.getConnection())
                .willThrow(new RedisConnectionFailureException("Redis에 연결할 수 없습니다."));

        assertThat(starter.attempt()).isFalse();

        verify(container, never()).start();
    }

    @Test
    @DisplayName("구독 시작이 실패하면 구독을 멈추고 실패로 알린다")
    void stopsWhenStartFails() {
        given(connectionFactory.getConnection()).willReturn(connection);
        willThrow(new RedisConnectionFailureException("구독 실패")).given(container).start();

        assertThat(starter.attempt()).isFalse();

        verify(container).stop();
    }

    @Test
    @DisplayName("재시도 간격은 5초에서 두 배씩 늘어나 60초에서 멈춘다")
    void backsOffUpToOneMinute() {
        assertThat(IntStream.range(0, 7).mapToObj(WsRelaySubscriptionStarter::delayAfter).toList())
                .containsExactly(
                        Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20),
                        Duration.ofSeconds(40), Duration.ofSeconds(60), Duration.ofSeconds(60),
                        Duration.ofSeconds(60));
    }
}
