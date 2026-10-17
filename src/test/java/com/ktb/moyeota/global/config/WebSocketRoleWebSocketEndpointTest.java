package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.websocket.subscription.ChatRoomParticipantChecker;
import com.ktb.moyeota.global.websocket.subscription.RestChatRoomParticipantChecker;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "APP_ROLE=websocket")
class WebSocketRoleWebSocketEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ChatRoomParticipantChecker participantChecker;

    @Test
    @DisplayName("websocket 역할은 웹소켓 연결을 받는다")
    void acceptsWebSocketHandshake() throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:3000");

        WebSocketSession session = new StandardWebSocketClient()
                .execute(new AbstractWebSocketHandler() {
                }, headers, URI.create("ws://localhost:" + port + "/api/wss"))
                .get(3, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();
        session.close();
    }

    @Test
    @DisplayName("websocket 역할은 구독할 때 참여 여부를 rest 내부 API로 확인한다")
    void checksParticipantThroughRest() {
        assertThat(participantChecker).isInstanceOf(RestChatRoomParticipantChecker.class);
    }
}
