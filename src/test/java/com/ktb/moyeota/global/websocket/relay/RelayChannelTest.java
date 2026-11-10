package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    @DisplayName("구독 패턴은 같은 환경과 버전의 채널을 모두 받는다")
    void pattern() {
        assertThat(relayChannel.pattern()).isEqualTo("moyeota:prod:ws:v1:*");
    }

    @Test
    @DisplayName("채팅방 채널을 채팅방 대상과 id로 해석한다")
    void parsesRoomChannel() {
        assertThat(relayChannel.parse("moyeota:prod:ws:v1:room:7"))
                .contains(new RelayTarget(RelayTarget.Kind.ROOM, 7L));
    }

    @Test
    @DisplayName("사용자 채널을 사용자 대상과 id로 해석한다")
    void parsesUserChannel() {
        assertThat(relayChannel.parse("moyeota:prod:ws:v1:user:12"))
                .contains(new RelayTarget(RelayTarget.Kind.USER, 12L));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "moyeota:dev:ws:v1:room:7",
            "moyeota:prod:ws:v2:room:7",
            "moyeota:prod:ws:v1:chat:7",
            "moyeota:prod:ws:v1:room:abc",
            "moyeota:prod:ws:v1:room:7:extra",
            "moyeota:prod:ws:v1:room"
    })
    @DisplayName("환경·버전·대상·id 형식이 맞지 않는 채널은 해석하지 않는다")
    void rejectsUnknownChannel(String channel) {
        assertThat(relayChannel.parse(channel)).isEmpty();
    }
}
