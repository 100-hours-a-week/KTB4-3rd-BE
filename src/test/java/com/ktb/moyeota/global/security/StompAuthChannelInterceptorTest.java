package com.ktb.moyeota.global.security;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final Long ROOM_ID = 501L;
    private static final Long USER_ID = 42L;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtDecoder, companionParticipantRepository);
    }

    @Nested
    @DisplayName("CONNECT 인증")
    class Connect {

        @Test
        @DisplayName("유효한 Bearer 토큰이면 세션에 userId를 붙인다")
        void authenticatesValidToken() {
            given(jwtDecoder.decode("valid-token")).willReturn(validJwt());

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer valid-token");
            // 실제 STOMP 처리 과정에서는 프레임 디코딩 시점에 헤더가 mutable 상태로 넘어와서
            // preSend()가 accessor.setUser(...)로 갱신할 수 있다. 여기서는 메시지를 직접 만들기 때문에
            // setLeaveMutable(true)로 그 상태를 재현해줘야 한다 — 안 그러면 getMessageHeaders() 호출 시점에
            // 헤더가 immutable로 굳어버려서 preSend() 내부의 setUser()가 IllegalStateException을 던진다.
            accessor.setLeaveMutable(true);
            Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(connectMessage, null);

            assertThat(result).isNotNull();
            assertThat(accessor.getUser().getName()).isEqualTo(String.valueOf(USER_ID));
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 인증 실패")
        void rejectsMissingHeader() {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("토큰이 유효하지 않으면 인증 실패")
        void rejectsInvalidToken() {
            given(jwtDecoder.decode("bad-token")).willThrow(new JwtException("invalid"));

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer bad-token");
            Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("SUBSCRIBE 검증")
    class Subscribe {

        @Test
        @DisplayName("채팅방 활성 참여자면 구독이 그대로 통과된다")
        void passesForActiveParticipant() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(anyParticipant()));

            Message<byte[]> subscribeMessage = subscribeMessage("/sub/chat/" + ROOM_ID, USER_ID);

            Message<?> result = interceptor.preSend(subscribeMessage, null);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("비참여자의 구독은 조용히 취소된다")
        void dropsForNonParticipant() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.empty());

            Message<byte[]> subscribeMessage = subscribeMessage("/sub/chat/" + ROOM_ID, USER_ID);

            Message<?> result = interceptor.preSend(subscribeMessage, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("채팅방 구독이 아니면 참여자 검증을 하지 않는다")
        void ignoresNonChatDestinations() {
            Message<byte[]> subscribeMessage = subscribeMessage("/sub/other/123", USER_ID);

            Message<?> result = interceptor.preSend(subscribeMessage, null);

            assertThat(result).isNotNull();
            verify(companionParticipantRepository, never()).findActiveByChatRoomIdAndUserId(any(), any());
        }

        private Message<byte[]> subscribeMessage(String destination, Long userId) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination(destination);
            accessor.setUser(principal(userId));
            return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        }

        private Principal principal(Long userId) {
            String name = String.valueOf(userId);
            return () -> name;
        }

        private CompanionParticipant anyParticipant() {
            return CompanionParticipant.join(companionPost(user("호스트")), user("참여자"));
        }
    }

    @Nested
    @DisplayName("방어 코드")
    class Defensive {

        @Test
        @DisplayName("STOMP 헤더가 없는 메시지는 그대로 통과시킨다")
        void passesThroughWhenNoStompHeaders() {
            Message<String> plainMessage = MessageBuilder.withPayload("no-stomp-headers").build();

            Message<?> result = interceptor.preSend(plainMessage, null);

            assertThat(result).isSameAs(plainMessage);
        }
    }

    private Jwt validJwt() {
        return Jwt.withTokenValue("valid-token")
                .header("alg", "none")
                .claim("sub", String.valueOf(USER_ID))
                .build();
    }
}
