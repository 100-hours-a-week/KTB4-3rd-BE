package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@Import(RedisTestContainerConfig.class)
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("테스트 컨테이너 Redis에 쓰고 읽을 수 있다")
    void writesAndReads() {
        redisTemplate.opsForValue().set("map-pins:cell:0:3749:12702", "[]", Duration.ofMinutes(1));

        assertThat(redisTemplate.opsForValue().get("map-pins:cell:0:3749:12702")).isEqualTo("[]");
        assertThat(redisTemplate.getExpire("map-pins:cell:0:3749:12702")).isBetween(1L, 60L);
    }
}
