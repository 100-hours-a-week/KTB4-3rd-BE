package com.ktb.moyeota.domain.notification.repository;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class NotificationRepositoryTest {

    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 9, 6, 9, 0, 0, 500_000_000);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 9, 10, 0, 123_456_000);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User recipient;
    private User other;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        recipient = persist(user("수신자"));
        other = persist(user("다른사람"));
        Companion companion = persist(companionPost(recipient));
        chatRoom = persist(ChatRoom.create(companion));
    }

    @Nested
    @DisplayName("findByRecipientWithCursor")
    class FindByRecipientWithCursor {

        @Test
        @DisplayName("내 알림만 id 내림차순으로 조회한다")
        void onlyMineOrderByIdDesc() {
            Notification n1 = persist(unread(recipient));
            persist(unread(other));
            Notification n2 = persist(read(recipient));
            Notification n3 = persist(unread(recipient));

            List<Notification> result = notificationRepository.findByRecipientWithCursor(
                    recipient.getId(), false, null, PageRequest.of(0, 21));

            assertThat(result).extracting(Notification::getId)
                    .containsExactly(n3.getId(), n2.getId(), n1.getId());
        }

        @Test
        @DisplayName("unreadOnly=true면 read_at이 null인 알림만 조회한다")
        void unreadOnly() {
            Notification n1 = persist(unread(recipient));
            persist(read(recipient));
            Notification n3 = persist(unread(recipient));

            List<Notification> result = notificationRepository.findByRecipientWithCursor(
                    recipient.getId(), true, null, PageRequest.of(0, 21));

            assertThat(result).extracting(Notification::getId).containsExactly(n3.getId(), n1.getId());
        }

        @Test
        @DisplayName("cursor가 있으면 cursor보다 작은 id만 조회한다")
        void cursor() {
            Notification n1 = persist(unread(recipient));
            Notification n2 = persist(unread(recipient));
            persist(unread(recipient));

            List<Notification> result = notificationRepository.findByRecipientWithCursor(
                    recipient.getId(), false, n2.getId(), PageRequest.of(0, 21));

            assertThat(result).extracting(Notification::getId).containsExactly(n1.getId());
        }

        @Test
        @DisplayName("Pageable 크기만큼만 조회한다")
        void limit() {
            for (int i = 0; i < 25; i++) {
                persist(unread(recipient));
            }

            List<Notification> result = notificationRepository.findByRecipientWithCursor(
                    recipient.getId(), false, null, PageRequest.of(0, 21));

            assertThat(result).hasSize(21);
        }
    }

    @Nested
    @DisplayName("markReadIfUnread")
    class MarkReadIfUnread {

        @Test
        @DisplayName("안읽은 내 알림이면 1을 반환하고 read_at을 기록한다")
        void unreadMine() {
            Notification notification = persist(unread(recipient));

            int updated = notificationRepository.markReadIfUnread(notification.getId(), recipient.getId(), NOW);
            entityManager.clear();

            assertThat(updated).isEqualTo(1);
            assertThat(entityManager.find(Notification.class, notification.getId()).getReadAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("이미 읽은 알림이면 0을 반환하고 read_at을 덮어쓰지 않는다")
        void alreadyRead() {
            Notification notification = persist(read(recipient));

            int updated = notificationRepository.markReadIfUnread(notification.getId(), recipient.getId(), NOW);
            entityManager.clear();

            assertThat(updated).isZero();
            assertThat(entityManager.find(Notification.class, notification.getId()).getReadAt()).isEqualTo(READ_AT);
        }

        @Test
        @DisplayName("다른 사람의 알림이면 0을 반환하고 바꾸지 않는다")
        void othersNotification() {
            Notification notification = persist(unread(other));

            int updated = notificationRepository.markReadIfUnread(notification.getId(), recipient.getId(), NOW);
            entityManager.clear();

            assertThat(updated).isZero();
            assertThat(entityManager.find(Notification.class, notification.getId()).getReadAt()).isNull();
        }
    }

    @Nested
    @DisplayName("findReadAtByIdAndRecipientId")
    class FindReadAtByIdAndRecipientId {

        @Test
        @DisplayName("내 알림의 read_at을 조회한다")
        void mine() {
            Notification notification = persist(read(recipient));

            assertThat(notificationRepository.findReadAtByIdAndRecipientId(notification.getId(), recipient.getId()))
                    .contains(READ_AT);
        }

        @Test
        @DisplayName("다른 사람의 알림이면 비어 있다")
        void others() {
            Notification notification = persist(read(other));

            assertThat(notificationRepository.findReadAtByIdAndRecipientId(notification.getId(), recipient.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("없는 id면 비어 있다")
        void notExists() {
            assertThat(notificationRepository.findReadAtByIdAndRecipientId(999_999L, recipient.getId())).isEmpty();
        }
    }

    private Notification unread(User to) {
        return Notification.createForChatRoom(
                to, NotificationType.MATCHING_COMPLETED, 1L, chatRoom, "매칭이 완료됐어요");
    }

    private Notification read(User to) {
        Notification notification = unread(to);
        ReflectionTestUtils.setField(notification, "readAt", READ_AT);
        return notification;
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
