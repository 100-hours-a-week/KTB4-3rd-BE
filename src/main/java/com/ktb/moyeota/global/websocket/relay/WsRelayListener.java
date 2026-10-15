package com.ktb.moyeota.global.websocket.relay;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsRelayListener implements MessageListener {

    private final RelayChannel relayChannel;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<RoomRelayHandler> roomRelayHandler;
    private final ObjectProvider<UserRelayHandler> userRelayHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        Optional<RelayTarget> target = relayChannel.parse(channel);
        if (target.isEmpty()) {
            log.warn("[WS_RELAY_UNKNOWN_CHANNEL] 해석할 수 없는 채널입니다. channel={}", channel);
            return;
        }

        RelayMessage relayMessage;
        try {
            relayMessage = jsonMapper.readValue(message.getBody(), RelayMessage.class);
        } catch (JacksonException e) {
            log.warn("[WS_RELAY_MALFORMED_MESSAGE] 메시지를 읽지 못했습니다. channel={}", channel, e);
            return;
        }

        try {
            dispatch(target.get(), relayMessage);
        } catch (RuntimeException e) {
            log.error("[WS_RELAY_HANDLE_FAILED] 메시지를 처리하지 못했습니다. channel={}", channel, e);
        }
    }

    private void dispatch(RelayTarget target, RelayMessage message) {
        switch (target.kind()) {
            case ROOM -> {
                RoomRelayHandler handler = roomRelayHandler.getIfAvailable();
                if (handler == null) {
                    logNoHandler(target);
                    return;
                }
                handler.handle(target.id(), message);
            }
            case USER -> {
                UserRelayHandler handler = userRelayHandler.getIfAvailable();
                if (handler == null) {
                    logNoHandler(target);
                    return;
                }
                handler.handle(target.id(), message);
            }
        }
    }

    private static void logNoHandler(RelayTarget target) {
        log.warn("[WS_RELAY_NO_HANDLER] 처리기가 없어 메시지를 버립니다. kind={} id={}", target.kind(), target.id());
    }
}
