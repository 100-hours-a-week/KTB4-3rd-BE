package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.taxipot.scheduler.TaxiPotRideStartScheduler;
import com.ktb.moyeota.global.outbox.publisher.OutboxPublishScheduler;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "APP_ROLE=rest")
class RestRoleWebSocketEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

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

    @Test
    @DisplayName("rest 역할에서는 스케줄러와 아웃박스 발행기를 켜지 않는다")
    void runsNoSchedulers() {
        assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OutboxPublishScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TaxiPotRideStartScheduler.class)).isEmpty();
    }
}
