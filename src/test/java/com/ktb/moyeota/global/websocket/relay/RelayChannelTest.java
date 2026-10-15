package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelayChannelTest {

    private final RelayChannel relayChannel = new RelayChannel(new RelayProperties("prod"));

    @Test
    @DisplayName("채팅방 채널 이름에 환경, 버전, 채팅방 id를 넣는다")
    void roomChannel() {
        assertThat(relayChannel.room(7L)).isEqualTo("moyeota:prod:ws:v1:room:7");
    }

    @Test
    @DisplayName("사용자 채널 이름에 환경, 버전, 사용자 id를 넣는다")
    void userChannel() {
        assertThat(relayChannel.user(12L)).isEqualTo("moyeota:prod:ws:v1:user:12");
    }
}
