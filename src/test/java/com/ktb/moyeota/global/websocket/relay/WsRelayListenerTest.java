package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class WsRelayListenerTest {

    private static final String ROOM_BODY =
            "{\"destination\":\"/sub/chat/7\",\"payload\":{\"message_id\":900,\"content\":\"출발지 앞이에요\"}}";

    private final RelayChannel relayChannel = new RelayChannel(new RelayProperties("local"));
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Mock
    private RoomRelayHandler roomRelayHandler;

    @Mock
    private UserRelayHandler userRelayHandler;

    @Test
    @DisplayName("채팅방 채널 메시지는 채팅방 처리기로 채팅방 id와 함께 넘긴다")
    void dispatchesRoomMessage() {
        listenerWithHandlers().onMessage(message("moyeota:local:ws:v1:room:7", ROOM_BODY), null);

        ArgumentCaptor<RelayMessage> captured = ArgumentCaptor.forClass(RelayMessage.class);
        verify(roomRelayHandler).handle(eq(7L), captured.capture());
        assertThat(captured.getValue().destination()).isEqualTo("/sub/chat/7");
        assertThat(captured.getValue().payload())
                .isEqualTo(Map.of("message_id", 900, "content", "출발지 앞이에요"));
        verifyNoInteractions(userRelayHandler);
    }

    @Test
    @DisplayName("사용자 채널 메시지는 사용자 처리기로 사용자 id와 함께 넘긴다")
    void dispatchesUserMessage() {
        String body = "{\"destination\":\"/user/sub/settlement\",\"payload\":{\"settlement_id\":77}}";

        listenerWithHandlers().onMessage(message("moyeota:local:ws:v1:user:12", body), null);

        ArgumentCaptor<RelayMessage> captured = ArgumentCaptor.forClass(RelayMessage.class);
        verify(userRelayHandler).handle(eq(12L), captured.capture());
        assertThat(captured.getValue().destination()).isEqualTo("/user/sub/settlement");
        verifyNoInteractions(roomRelayHandler);
    }

    @Test
    @DisplayName("해석할 수 없는 채널의 메시지는 처리기로 넘기지 않고 버린다")
    void ignoresUnknownChannel() {
        assertThatCode(() -> listenerWithHandlers()
                .onMessage(message("moyeota:local:ws:v1:chat:7", ROOM_BODY), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(roomRelayHandler, userRelayHandler);
    }

    @Test
    @DisplayName("JSON이 깨진 메시지는 처리기로 넘기지 않고 버린다")
    void ignoresMalformedBody() {
        assertThatCode(() -> listenerWithHandlers()
                .onMessage(message("moyeota:local:ws:v1:room:7", "{not-json"), null))
                .doesNotThrowAnyException();

        verifyNoInteractions(roomRelayHandler, userRelayHandler);
    }

    @Test
    @DisplayName("처리기가 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsHandlerFailure() {
        willThrow(new IllegalStateException("처리 실패")).given(roomRelayHandler).handle(anyLong(), any());

        assertThatCode(() -> listenerWithHandlers()
                .onMessage(message("moyeota:local:ws:v1:room:7", ROOM_BODY), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("처리기 구현이 없으면 예외 없이 메시지를 버린다")
    void dropsWhenNoHandler() {
        WsRelayListener listener = listener(new StaticListableBeanFactory());

        assertThatCode(() -> listener.onMessage(message("moyeota:local:ws:v1:room:7", ROOM_BODY), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onMessage(message("moyeota:local:ws:v1:user:12", ROOM_BODY), null))
                .doesNotThrowAnyException();
    }

    private WsRelayListener listenerWithHandlers() {
        return listener(new StaticListableBeanFactory(Map.of(
                "roomRelayHandler", roomRelayHandler,
                "userRelayHandler", userRelayHandler)));
    }

    private WsRelayListener listener(StaticListableBeanFactory beanFactory) {
        return new WsRelayListener(relayChannel, jsonMapper,
                beanFactory.getBeanProvider(RoomRelayHandler.class),
                beanFactory.getBeanProvider(UserRelayHandler.class));
    }

    private static DefaultMessage message(String channel, String body) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
