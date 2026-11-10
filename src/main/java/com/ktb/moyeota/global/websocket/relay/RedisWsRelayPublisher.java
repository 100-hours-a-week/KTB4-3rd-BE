package com.ktb.moyeota.global.websocket.relay;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWsRelayPublisher implements WsRelayPublisher {

    private static final String PUBLISH_METRIC = "ws.relay.publish";
    private static final String ROOM = "room";
    private static final String USER = "user";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RelayChannel relayChannel;
    private final MeterRegistry meterRegistry;

    @Override
    public void toRoom(Long roomId, String destination, Object payload) {
        publish(ROOM, relayChannel.room(roomId), new RelayMessage(destination, payload));
    }

    @Override
    public void toUser(Long userId, String destination, Object payload) {
        publish(USER, relayChannel.user(userId), new RelayMessage(destination, payload));
    }

    private void publish(String target, String channel, RelayMessage message) {
        try {
            redisTemplate.convertAndSend(channel, jsonMapper.writeValueAsString(message));
            count(target, SUCCESS);
        } catch (JacksonException e) {
            log.error("[WS_RELAY_PUBLISH_FAILED] 메시지를 JSON으로 바꾸지 못했습니다. channel={}", channel, e);
            count(target, FAILURE);
        } catch (DataAccessException e) {
            log.warn("[WS_RELAY_PUBLISH_FAILED] Redis 발행에 실패했습니다. channel={}", channel, e);
            count(target, FAILURE);
        }
    }

    private void count(String target, String result) {
        meterRegistry.counter(PUBLISH_METRIC, "target", target, "result", result).increment();
    }
}
