package com.ktb.moyeota.domain.chat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.fixture.CompanionFixture;
import com.ktb.moyeota.fixture.UserFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageItemTest {

    private final User sender = UserFixture.user(7L, "제리");

    @Test
    @DisplayName("택시팟 채팅방 메시지는 발신자를 실명으로 내린다")
    void taxiPotSenderUsesRealName() {
        Companion taxiPot = CompanionFixture.taxiPot(sender, CompanionStatus.RECRUITING);

        MessageItem item = MessageItem.from(textIn(taxiPot), "https://cdn.moyeota.test/profile/a.png");

        assertThat(item.sender()).isEqualTo(new MessageItem.Sender(7L, "홍길동", null, "https://cdn.moyeota.test/profile/a.png"));
    }

    @Test
    @DisplayName("카풀 채팅방 메시지도 발신자를 실명으로 내린다")
    void carpoolSenderUsesRealName() {
        Companion carpool = CompanionFixture.carpool(sender, CompanionStatus.RECRUITING, 1);

        MessageItem item = MessageItem.from(textIn(carpool), null);

        assertThat(item.sender()).isEqualTo(new MessageItem.Sender(7L, "홍길동", null, null));
    }

    @Test
    @DisplayName("동행모집 채팅방 메시지는 발신자를 닉네임으로 내린다")
    void companionPostSenderUsesNickname() {
        Companion companionPost = CompanionFixture.companionPost(sender);

        MessageItem item = MessageItem.from(textIn(companionPost), null);

        assertThat(item.sender()).isEqualTo(new MessageItem.Sender(7L, null, "제리", null));
    }

    @Test
    @DisplayName("송금 요청 메시지는 보낸 사람 없이 문구와 함께 받은 정산 값을 그대로 싣는다")
    void transferRequestedCarriesSettlement() {
        ChatRoom chatRoom = ChatRoom.create(CompanionFixture.taxiPot(sender, CompanionStatus.COMPLETED));
        MessageItem.Settlement settlement = new MessageItem.Settlement(
                77L, 26000, 13000, new MessageItem.Payee(1L, "김방장", "kakao", "00000000000"));

        MessageItem item = MessageItem.from(
                Message.transferRequestedSystemMessage(chatRoom, "client-key", "방장이 정산을 요청했어요"), null, settlement);

        assertThat(item.type()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
        assertThat(item.content()).isEqualTo("방장이 정산을 요청했어요");
        assertThat(item.settlement()).isEqualTo(settlement);
        assertThat(item.sender()).isNull();
    }

    @Test
    @DisplayName("미터기 요청 · 정산 완료 메시지는 보낸 사람 · 정산 값 없이 문구만 싣는다")
    void settlementNoticeCarriesContentOnly() {
        ChatRoom chatRoom = ChatRoom.create(CompanionFixture.taxiPot(sender, CompanionStatus.COMPLETED));

        MessageItem meter = MessageItem.from(
                Message.meterRequestedSystemMessage(chatRoom, "client-key", "택시 미터기 사진을 보내주세요"), null);
        MessageItem completed = MessageItem.from(
                Message.settlementCompletedSystemMessage(chatRoom, "client-key", "정산이 완료됐어요"), null);

        assertThat(meter.type()).isEqualTo(MessageType.SYSTEM_METER_REQUESTED);
        assertThat(meter.content()).isEqualTo("택시 미터기 사진을 보내주세요");
        assertThat(completed.type()).isEqualTo(MessageType.SYSTEM_SETTLEMENT_COMPLETED);
        assertThat(completed.content()).isEqualTo("정산이 완료됐어요");
        assertThat(List.of(meter, completed)).allSatisfy(item -> {
            assertThat(item.sender()).isNull();
            assertThat(item.settlement()).isNull();
        });
    }

    private Message textIn(Companion companion) {
        return Message.createGeneralMessage(ChatRoom.create(companion), sender, "client-key", "안녕하세요");
    }
}
