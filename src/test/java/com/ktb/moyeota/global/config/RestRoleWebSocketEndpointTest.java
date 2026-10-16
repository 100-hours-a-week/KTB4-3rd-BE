package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "APP_ROLE=rest")
class RestRoleWebSocketEndpointTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("rest 역할로 기동하고, 웹소켓 연결은 404로 거절한다")
    void rejectsWebSocketHandshake() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:3000");

        assertThatThrownBy(() -> new StandardWebSocketClient()
                .execute(new AbstractWebSocketHandler() {
                }, headers, URI.create("ws://localhost:" + port + "/api/wss"))
                .get(3, TimeUnit.SECONDS))
                .rootCause()
                .hasMessageContaining("[404]");
    }
}
