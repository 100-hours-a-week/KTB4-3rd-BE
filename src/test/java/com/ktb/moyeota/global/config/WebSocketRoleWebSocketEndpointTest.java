package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
