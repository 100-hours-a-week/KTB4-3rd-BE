package com.ktb.moyeota.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationWithoutTargetTest {

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"CARPOOL_REQUEST_REJECTED", "CARPOOL_REQUEST_EXPIRED"})
    @DisplayName("createWithoutTarget: 카풀 요청 거절 · 만료 알림은 이동 대상 없이 만든다")
    void createsWithoutTarget(NotificationType type) {
        Notification notification = Notification.createWithoutTarget(mock(User.class), type, 7L, "문구");

        assertThat(notification.getType()).isEqualTo(type);
        assertThat(notification.getEventId()).isEqualTo(7L);
        assertThat(notification.getTargetChatRoom()).isNull();
        assertThat(notification.getTargetPost()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"CARPOOL_REQUEST_REJECTED", "CARPOOL_REQUEST_EXPIRED"})
    @DisplayName("createWithoutTarget: 이동 대상이 있는 타입이면 예외다")
    void rejectsTargetedTypes(NotificationType type) {
        assertThatThrownBy(() -> Notification.createWithoutTarget(mock(User.class), type, 7L, "문구"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createForChatRoom: 이동 대상이 없는 타입이면 예외다")
    void chatRoomFactoryRejectsNoTargetTypes() {
        assertThatThrownBy(() -> Notification.createForChatRoom(mock(User.class),
                NotificationType.CARPOOL_REQUEST_REJECTED, 7L, mock(ChatRoom.class), "문구"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
