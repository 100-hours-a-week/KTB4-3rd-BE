package com.ktb.moyeota.domain.chat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.fixture.CompanionFixture;
import com.ktb.moyeota.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageItemTest {

    private final User sender = UserFixture.user(7L, "제리");

    @Test
    @DisplayName("택시팟 채팅방 메시지는 발신자를 실명으로 내린다")
    void taxiPotSenderUsesRealName() {
        Companion taxiPot = CompanionFixture.taxiPot(sender, CompanionStatus.RECRUITING);

        MessageItem item = MessageItem.from(textIn(taxiPot));

        assertThat(item.sender()).isEqualTo(new MessageItem.Sender(7L, "홍길동", null, null));
    }

    @Test
    @DisplayName("동행모집 채팅방 메시지는 발신자를 닉네임으로 내린다")
    void companionPostSenderUsesNickname() {
        Companion companionPost = CompanionFixture.companionPost(sender);

        MessageItem item = MessageItem.from(textIn(companionPost));

        assertThat(item.sender()).isEqualTo(new MessageItem.Sender(7L, null, "제리", null));
    }

    private Message textIn(Companion companion) {
        return Message.createGeneralMessage(ChatRoom.create(companion), sender, "client-key", "안녕하세요");
    }
}
