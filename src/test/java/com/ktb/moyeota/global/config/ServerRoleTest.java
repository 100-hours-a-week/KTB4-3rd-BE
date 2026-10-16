package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ServerRoleTest {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("APP_ROLE이 없으면 모든 역할을 맡는다")
    void defaultsToAll(String value) {
        assertThat(ServerRole.from(value)).isEqualTo(ServerRole.ALL);
    }

    @Test
    @DisplayName("APP_ROLE 값을 역할로 바꾼다")
    void parsesServiceName() {
        assertThat(ServerRole.from("rest")).isEqualTo(ServerRole.REST);
        assertThat(ServerRole.from("websocket")).isEqualTo(ServerRole.WEBSOCKET);
        assertThat(ServerRole.from("all")).isEqualTo(ServerRole.ALL);
    }

    @Test
    @DisplayName("알 수 없는 APP_ROLE 값이면 기동을 막는다")
    void rejectsUnknownServiceName() {
        assertThatThrownBy(() -> ServerRole.from("websockets"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("웹소켓 연결은 websocket과 all 역할만 받는다")
    void acceptsWebSocketOnlyForWebSocketAndAll() {
        assertThat(ServerRole.REST.acceptsWebSocket()).isFalse();
        assertThat(ServerRole.WEBSOCKET.acceptsWebSocket()).isTrue();
        assertThat(ServerRole.ALL.acceptsWebSocket()).isTrue();
    }
}
