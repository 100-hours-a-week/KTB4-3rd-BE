package com.ktb.moyeota.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 9, 0, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 10, 28, 21, 0, 0);

    @Test
    @DisplayName("createSettlementReminder: 정산 id를 event_id로, 채팅방을 이동 대상으로 만든다")
    void createSettlementReminder() {
        ChatRoom chatRoom = mock(ChatRoom.class);

        Notification notification = Notification.createSettlementReminder(
                mock(User.class), 77L, chatRoom, "문구", NOW);

        assertThat(notification.getType()).isEqualTo(NotificationType.SETTLEMENT_COMPLETION_REMINDER);
        assertThat(notification.getEventId()).isEqualTo(77L);
        assertThat(notification.getTargetChatRoom()).isSameAs(chatRoom);
        assertThat(notification.getTargetPost()).isNull();
        assertThat(notification.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("remind: 문구와 생성 시각을 바꾸고 안읽음으로 되돌린다")
    void remind() {
        Notification notification = Notification.createSettlementReminder(
                mock(User.class), 77L, mock(ChatRoom.class), "이전 문구", NOW);
        ReflectionTestUtils.setField(notification, "readAt", NOW.plusHours(1));

        notification.remind("새 문구", LATER);

        assertThat(notification.getContent()).isEqualTo("새 문구");
        assertThat(notification.getCreatedAt()).isEqualTo(LATER);
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("remind: 리마인드 알림이 아니면 다시 보낼 수 없다")
    void remindOnlyReminder() {
        Notification notification = Notification.createForChatRoom(
                mock(User.class), NotificationType.SETTLEMENT_REQUESTED, 77L, mock(ChatRoom.class), "문구");

        assertThatThrownBy(() -> notification.remind("새 문구", LATER))
                .isInstanceOf(IllegalStateException.class);
    }
}
