package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ServerRoleTest {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("APP_ROLE이 없으면 모든 역할을 맡는다")
    void defaultsToAll(String value) {
        assertThat(ServerRole.from(value)).containsExactly(ServerRole.ALL);
    }

    @Test
    @DisplayName("APP_ROLE 값을 역할로 바꾼다")
    void parsesServiceName() {
        assertThat(ServerRole.from("rest")).containsExactly(ServerRole.REST);
        assertThat(ServerRole.from("websocket")).containsExactly(ServerRole.WEBSOCKET);
        assertThat(ServerRole.from("matching")).containsExactly(ServerRole.MATCHING);
        assertThat(ServerRole.from("worker")).containsExactly(ServerRole.WORKER);
        assertThat(ServerRole.from("all")).containsExactly(ServerRole.ALL);
    }

    @Test
    @DisplayName("쉼표로 나눈 여러 역할을 한 서버에 함께 켠다")
    void parsesSeveralRoles() {
        assertThat(ServerRole.from("matching, worker"))
                .containsExactlyInAnyOrder(ServerRole.MATCHING, ServerRole.WORKER);
        assertThat(ServerRole.from("rest,matching,worker"))
                .containsExactlyInAnyOrder(ServerRole.REST, ServerRole.MATCHING, ServerRole.WORKER);
    }

    @Test
    @DisplayName("알 수 없는 APP_ROLE 값이면 기동을 막는다")
    void rejectsUnknownServiceName() {
        assertThatThrownBy(() -> ServerRole.from("websockets"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ServerRole.from("matching,"))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rest,websocket", "websocket,worker", "all,matching"})
    @DisplayName("websocket과 all은 다른 역할과 함께 켜지 못한다")
    void rejectsWebSocketOrAllWithOthers(String value) {
        assertThatThrownBy(() -> ServerRole.from(value))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("웹소켓 연결은 websocket과 all 역할만 받는다")
    void acceptsWebSocketOnlyForWebSocketAndAll() {
        assertThat(ServerRole.REST.acceptsWebSocket()).isFalse();
        assertThat(ServerRole.WEBSOCKET.acceptsWebSocket()).isTrue();
        assertThat(ServerRole.MATCHING.acceptsWebSocket()).isFalse();
        assertThat(ServerRole.WORKER.acceptsWebSocket()).isFalse();
        assertThat(ServerRole.ALL.acceptsWebSocket()).isTrue();
    }
}
