package com.ktb.moyeota.domain.auth.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

@DataRedisTest
@Import({RedisTestContainerConfig.class, RedisSessionStore.class, RedisSessionStoreTest.FixedClock.class})
@TestPropertySource(properties = "moyeota.auth.session-store=redis")
class RedisSessionStoreTest extends SessionStoreContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime EXPIRES = NOW.plusDays(7);

    @Autowired
    private RedisSessionStore sessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    protected SessionStore store() {
        return sessionStore;
    }

    @BeforeEach
    void clearAuthKeys() {
        Set<String> keys = redisTemplate.keys("auth:*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("세션 · 토큰 · 사용자 목록 키는 최대 유효 시각까지만 남는다")
    void expiresAtAbsoluteExpiry() {
        Long sessionId = sessionStore.create(1L, "hash-ttl", EXPIRES);
        sessionStore.rotate(sessionId, "hash-ttl", "hash-ttl-next", NOW);

        for (String key : List.of(RedisSessionStore.SESSION_PREFIX + sessionId,
                RedisSessionStore.SESSION_PREFIX + sessionId + ":tokens",
                RedisSessionStore.TOKEN_PREFIX + "hash-ttl",
                RedisSessionStore.TOKEN_PREFIX + "hash-ttl-next",
                RedisSessionStore.USER_PREFIX + "1:sessions")) {
            assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS))
                    .as(key)
                    .isBetween(Duration.ofDays(7).minusMinutes(1).toSeconds(), Duration.ofDays(7).toSeconds());
        }
    }

    @Test
    @DisplayName("같은 토큰으로 동시에 회전해도 한 번만 성공하고 새 토큰도 하나만 생긴다")
    void concurrentRotateSucceedsOnce() throws Exception {
        Long sessionId = sessionStore.create(1L, "hash-race", EXPIRES);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> attempts = IntStream.range(0, 8)
                    .<Callable<Boolean>>mapToObj(
                            i -> () -> sessionStore.rotate(sessionId, "hash-race", "hash-race-" + i, NOW))
                    .toList();

            List<Future<Boolean>> results = executor.invokeAll(attempts);

            long succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(redisTemplate.keys(RedisSessionStore.TOKEN_PREFIX + "hash-race-*")).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("세션을 지우면 사용자 세션 목록에서도 빠지고, 다른 세션은 목록에 남는다")
    void deleteSessionRemovesFromUserSessions() {
        Long first = sessionStore.create(1L, "hash-first", EXPIRES);
        Long second = sessionStore.create(1L, "hash-second", EXPIRES);

        sessionStore.deleteSession(first);

        assertThat(redisTemplate.opsForSet().members(RedisSessionStore.USER_PREFIX + "1:sessions"))
                .containsExactly(String.valueOf(second));
        assertThat(redisTemplate.hasKey(RedisSessionStore.SESSION_PREFIX + first)).isFalse();
        assertThat(redisTemplate.hasKey(RedisSessionStore.SESSION_PREFIX + first + ":tokens")).isFalse();
    }

    @Test
    @DisplayName("사용자의 세션을 모두 지우면 그 사용자의 세션 · 토큰 · 목록 키가 남지 않는다")
    void deleteAllByUserIdLeavesNoKeys() {
        Long sessionId = sessionStore.create(1L, "hash-all", EXPIRES);
        sessionStore.rotate(sessionId, "hash-all", "hash-all-next", NOW);

        sessionStore.deleteAllByUserId(1L);

        assertThat(redisTemplate.keys("auth:session:" + sessionId + "*")).isEmpty();
        assertThat(redisTemplate.keys(RedisSessionStore.TOKEN_PREFIX + "hash-all*")).isEmpty();
        assertThat(redisTemplate.hasKey(RedisSessionStore.USER_PREFIX + "1:sessions")).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        }
    }
}
