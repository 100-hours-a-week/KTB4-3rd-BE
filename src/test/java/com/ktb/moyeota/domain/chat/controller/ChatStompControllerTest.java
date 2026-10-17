package com.ktb.moyeota.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {

    private static final Long ROOM_ID = 30L;
    private static final Long USER_ID = 42L;

    @Mock
    private Principal principal;

    private final List<Sent> sent = new ArrayList<>();
    private final ChatStompController controller =
            new ChatStompController((roomId, userId, request) -> sent.add(new Sent(roomId, userId, request)));

    @Test
    @DisplayName("principal의 이름을 userId로 바꿔 방 id, 요청과 함께 보내기에 넘긴다")
    void passesUserIdFromPrincipal() {
        given(principal.getName()).willReturn(String.valueOf(USER_ID));
        ChatMessageSendRequest request = new ChatMessageSendRequest("1", "안녕하세요");

        controller.send(ROOM_ID, request, principal);

        assertThat(sent).containsExactly(new Sent(ROOM_ID, USER_ID, request));
    }

    private record Sent(Long roomId, Long userId, ChatMessageSendRequest request) {
    }
}
