package com.ktb.moyeota.domain.notification.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.dto.NotificationListQuery;
import com.ktb.moyeota.domain.notification.dto.NotificationListResponse;
import com.ktb.moyeota.domain.notification.dto.NotificationReadAllRequest;
import com.ktb.moyeota.domain.notification.dto.NotificationReadAllResult;
import com.ktb.moyeota.domain.notification.dto.NotificationReadResponse;
import com.ktb.moyeota.domain.notification.dto.NotificationResponse;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.exception.NotificationErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({NotificationService.class, NotificationServiceTest.FixedClockConfig.class})
class NotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 9, 6, 9, 10, 0, 123_456_789);
    private static final LocalDateTime TRUNCATED_NOW = LocalDateTime.of(2026, 9, 6, 9, 10, 0, 123_456_000);
    private static final LocalDateTime EARLIER_READ_AT = LocalDateTime.of(2026, 9, 6, 8, 0, 0, 500_000_000);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TestEntityManager entityManager;

    private User recipient;
    private User other;
    private ChatRoom chatRoom;
    private CommunityPost post;

    @BeforeEach
    void setUp() {
        recipient = persist(user("수신자"));
        other = persist(user("다른사람"));
        Companion companion = persist(companionPost(recipient));
        chatRoom = persist(ChatRoom.create(companion));
        post = persist(CommunityPost.create(recipient, "판교역 혼잡", "사람 많아요",
                new BigDecimal("37.394500"), new BigDecimal("127.111200")));
    }

    @Nested
    @DisplayName("findList - 탭")
    class Tab {

        @Test
        @DisplayName("전체 탭은 읽은 알림과 안읽은 알림을 구분 없이 20개 반환한다")
        void allTab() {
            for (int i = 0; i < 25; i++) {
                persist(i % 2 == 0 ? unread(recipient) : read(recipient));
            }

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery("all", null));

            assertThat(response.notifications()).hasSize(20);
            assertThat(response.notifications()).anyMatch(n -> n.readAt() == null);
            assertThat(response.notifications()).anyMatch(n -> n.readAt() != null);
        }

        @Test
        @DisplayName("tab이 없으면 전체 탭과 같다")
        void defaultTab() {
            persist(unread(recipient));
            persist(read(recipient));

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));

            assertThat(response.notifications()).hasSize(2);
        }

        @Test
        @DisplayName("안읽은 탭은 안읽은 알림만 20개 반환한다")
        void unreadTab() {
            for (int i = 0; i < 25; i++) {
                persist(unread(recipient));
                persist(read(recipient));
            }

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery("unread", null));

            assertThat(response.notifications()).hasSize(20);
            assertThat(response.notifications()).allMatch(n -> n.readAt() == null);
        }

        @Test
        @DisplayName("tab=read면 422 UNSUPPORTED_TAB 예외가 발생한다")
        void readTab() {
            assertThatThrownBy(() -> notificationService.findList(
                    recipient.getId(), new NotificationListQuery("read", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorCode.UNSUPPORTED_TAB);
        }

        @Test
        @DisplayName("tab=5면 400 INVALID_TAB 예외가 발생한다")
        void invalidTab() {
            assertThatThrownBy(() -> notificationService.findList(
                    recipient.getId(), new NotificationListQuery("5", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorCode.INVALID_TAB);
        }

        @Test
        @DisplayName("cursor가 숫자가 아니면 400 INVALID_CURSOR 예외가 발생한다")
        void invalidCursor() {
            assertThatThrownBy(() -> notificationService.findList(
                    recipient.getId(), new NotificationListQuery("all", "abc")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorCode.INVALID_CURSOR);
        }
    }

    @Nested
    @DisplayName("findList - 페이지네이션")
    class Paging {

        @Test
        @DisplayName("id 내림차순으로 정렬된다")
        void orderByIdDesc() {
            Notification n1 = persist(unread(recipient));
            Notification n2 = persist(unread(recipient));
            Notification n3 = persist(unread(recipient));

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));

            assertThat(response.notifications()).extracting(NotificationResponse::id)
                    .containsExactly(n3.getId(), n2.getId(), n1.getId());
        }

        @Test
        @DisplayName("정확히 20개면 다음 페이지가 없어 next_cursor가 null이다")
        void exactlyTwenty() {
            persistUnread(20);

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));

            assertThat(response.notifications()).hasSize(20);
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("21개면 20개만 반환하고 next_cursor는 20번째 알림의 id다")
        void twentyOne() {
            persistUnread(21);

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));

            assertThat(response.notifications()).hasSize(20);
            assertThat(response.nextCursor()).isEqualTo(String.valueOf(response.notifications().get(19).id()));
        }

        @Test
        @DisplayName("next_cursor로 다음 페이지를 조회하면 나머지가 중복·누락 없이 나온다")
        void nextPage() {
            List<Notification> saved = persistUnread(25);

            NotificationListResponse first = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));
            NotificationListResponse second = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, first.nextCursor()));

            List<Long> all = new ArrayList<>();
            first.notifications().forEach(n -> all.add(n.id()));
            second.notifications().forEach(n -> all.add(n.id()));

            assertThat(second.notifications()).hasSize(5);
            assertThat(second.nextCursor()).isNull();
            assertThat(all).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(saved.stream().map(Notification::getId).toList());
        }

        @Test
        @DisplayName("다른 사용자의 알림은 포함되지 않는다")
        void onlyMine() {
            Notification mine = persist(unread(recipient));
            persist(unread(other));

            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));

            assertThat(response.notifications()).extracting(NotificationResponse::id).containsExactly(mine.getId());
        }

        @Test
        @DisplayName("알림이 없으면 빈 목록과 next_cursor null을 반환한다")
        void empty() {
            NotificationListResponse response = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null));

            assertThat(response.notifications()).isEmpty();
            assertThat(response.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("findList - 응답 값")
    class ResponseValue {

        @Test
        @DisplayName("type별로 저장된 content가 그대로 반환된다")
        void contentByType() {
            persist(Notification.createForComment(recipient, 12L, post, "우림님이 댓글을 남겼어요"));
            persist(Notification.createForChatRoom(recipient, NotificationType.CARPOOL_REQUEST_RECEIVED,
                    31L, chatRoom, "민준님이 동승을 요청했어요"));
            persist(Notification.createForChatRoom(recipient, NotificationType.CARPOOL_REQUEST_ACCEPTED,
                    27L, chatRoom, "루디님이 동승 요청을 수락했어요"));
            persist(Notification.createForChatRoom(recipient, NotificationType.SETTLEMENT_REQUESTED,
                    7L, chatRoom, "정산 요청이 도착했어요"));
            persist(Notification.createForChatRoom(recipient, NotificationType.MATCHING_COMPLETED,
                    40L, chatRoom, "매칭이 완료됐어요"));

            Map<NotificationType, String> contents = notificationService.findList(
                            recipient.getId(), new NotificationListQuery(null, null))
                    .notifications().stream()
                    .collect(Collectors.toMap(NotificationResponse::type, NotificationResponse::content));

            assertThat(contents).containsExactlyInAnyOrderEntriesOf(Map.of(
                    NotificationType.COMMENT_CREATED, "우림님이 댓글을 남겼어요",
                    NotificationType.CARPOOL_REQUEST_RECEIVED, "민준님이 동승을 요청했어요",
                    NotificationType.CARPOOL_REQUEST_ACCEPTED, "루디님이 동승 요청을 수락했어요",
                    NotificationType.SETTLEMENT_REQUESTED, "정산 요청이 도착했어요",
                    NotificationType.MATCHING_COMPLETED, "매칭이 완료됐어요"));
        }

        @Test
        @DisplayName("댓글 알림은 target_post_id만, 채팅방 알림은 target_chat_room_id만 채워진다")
        void targetIds() {
            persist(Notification.createForComment(recipient, 12L, post, "우림님이 댓글을 남겼어요"));
            persist(Notification.createForChatRoom(recipient, NotificationType.MATCHING_COMPLETED,
                    40L, chatRoom, "매칭이 완료됐어요"));

            List<NotificationResponse> notifications = notificationService.findList(
                    recipient.getId(), new NotificationListQuery(null, null)).notifications();

            NotificationResponse matching = notifications.get(0);
            NotificationResponse comment = notifications.get(1);

            assertThat(matching.targetChatRoomId()).isEqualTo(chatRoom.getId());
            assertThat(matching.targetPostId()).isNull();
            assertThat(matching.eventId()).isEqualTo(40L);
            assertThat(comment.targetPostId()).isEqualTo(post.getId());
            assertThat(comment.targetChatRoomId()).isNull();
            assertThat(comment.eventId()).isEqualTo(12L);
        }
    }

    @Nested
    @DisplayName("markRead")
    class MarkRead {

        @Test
        @DisplayName("반올림될 시각(.123456789)이어도 응답 read_at과 DB read_at이 .123456으로 같다")
        void truncatedToMicros() {
            Notification notification = persist(unread(recipient));

            NotificationReadResponse response = notificationService.markRead(recipient.getId(), notification.getId());
            entityManager.flush();
            entityManager.clear();
            LocalDateTime stored = entityManager.find(Notification.class, notification.getId()).getReadAt();

            assertThat(response.readAt()).isEqualTo(TRUNCATED_NOW);
            assertThat(stored).isEqualTo(TRUNCATED_NOW);
            assertThat(stored).isEqualTo(response.readAt());
        }

        @Test
        @DisplayName("이미 읽은 알림이면 기존 read_at을 그대로 반환하고 덮어쓰지 않는다")
        void idempotent() {
            Notification notification = unread(recipient);
            ReflectionTestUtils.setField(notification, "readAt", EARLIER_READ_AT);
            persist(notification);

            NotificationReadResponse response = notificationService.markRead(recipient.getId(), notification.getId());
            entityManager.clear();

            assertThat(response.readAt()).isEqualTo(EARLIER_READ_AT);
            assertThat(entityManager.find(Notification.class, notification.getId()).getReadAt())
                    .isEqualTo(EARLIER_READ_AT);
        }

        @Test
        @DisplayName("다른 사용자의 알림이면 404 NOTIFICATION_NOT_FOUND이고 read_at은 바뀌지 않는다")
        void othersNotification() {
            Notification notification = persist(unread(other));

            assertThatThrownBy(() -> notificationService.markRead(recipient.getId(), notification.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);

            entityManager.clear();
            assertThat(entityManager.find(Notification.class, notification.getId()).getReadAt()).isNull();
        }

        @Test
        @DisplayName("없는 알림이면 404 NOTIFICATION_NOT_FOUND다")
        void notExists() {
            assertThatThrownBy(() -> notificationService.markRead(recipient.getId(), 999_999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("markAllRead")
    class MarkAllRead {

        @Test
        @DisplayName("화면 최상단 id 이하만 읽음 처리하고, 이후 도착한 알림은 안읽음으로 남긴다")
        void upToScreenTop() {
            Notification seen1 = persist(unread(recipient));
            Notification seenTop = persist(unread(recipient));
            Notification arrivedLater = persist(unread(recipient));

            NotificationReadAllResult result = notificationService.markAllRead(
                    recipient.getId(), new NotificationReadAllRequest(seenTop.getId()));
            entityManager.clear();

            assertThat(result.hasTarget()).isTrue();
            assertThat(readAtOf(seen1)).isEqualTo(TRUNCATED_NOW);
            assertThat(readAtOf(seenTop)).isEqualTo(TRUNCATED_NOW);
            assertThat(readAtOf(arrivedLater)).isNull();
        }

        @Test
        @DisplayName("이미 읽은 알림은 기존 read_at을 유지한다")
        void keepsAlreadyRead() {
            Notification alreadyRead = persist(read(recipient));
            Notification notYet = persist(unread(recipient));

            notificationService.markAllRead(recipient.getId(), new NotificationReadAllRequest(notYet.getId()));
            entityManager.clear();

            assertThat(readAtOf(alreadyRead)).isEqualTo(EARLIER_READ_AT);
            assertThat(readAtOf(notYet)).isEqualTo(TRUNCATED_NOW);
        }

        @Test
        @DisplayName("같은 요청을 다시 보내도 첫 read_at이 유지된다")
        void idempotent() {
            Notification notification = unread(recipient);
            persist(notification);
            notificationService.markAllRead(recipient.getId(), new NotificationReadAllRequest(notification.getId()));
            entityManager.clear();
            LocalDateTime first = readAtOf(notification);

            notificationService.markAllRead(recipient.getId(), new NotificationReadAllRequest(notification.getId()));
            entityManager.clear();

            assertThat(readAtOf(notification)).isEqualTo(first);
        }

        @Test
        @DisplayName("다른 사용자의 알림은 읽음 처리하지 않는다")
        void othersUntouched() {
            Notification others = persist(unread(other));
            Notification mine = persist(unread(recipient));

            notificationService.markAllRead(recipient.getId(), new NotificationReadAllRequest(mine.getId()));
            entityManager.clear();

            assertThat(readAtOf(others)).isNull();
        }

        @Test
        @DisplayName("max_notification_id가 null이면 아무것도 바꾸지 않는다")
        void nullDoesNothing() {
            Notification notification = persist(unread(recipient));

            NotificationReadAllResult result = notificationService.markAllRead(
                    recipient.getId(), new NotificationReadAllRequest(null));
            entityManager.clear();

            assertThat(result.hasTarget()).isFalse();
            assertThat(readAtOf(notification)).isNull();
        }

        @Test
        @DisplayName("max_notification_id가 0 이하면 400 INVALID_NOTIFICATION_ID이고 바꾸지 않는다")
        void invalidId() {
            Notification notification = persist(unread(recipient));

            assertThatThrownBy(() -> notificationService.markAllRead(
                    recipient.getId(), new NotificationReadAllRequest(0L)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorCode.INVALID_NOTIFICATION_ID);

            entityManager.clear();
            assertThat(readAtOf(notification)).isNull();
        }

        private LocalDateTime readAtOf(Notification notification) {
            return entityManager.find(Notification.class, notification.getId()).getReadAt();
        }
    }

    private Notification unread(User to) {
        return Notification.createForChatRoom(
                to, NotificationType.MATCHING_COMPLETED, 1L, chatRoom, "매칭이 완료됐어요");
    }

    private Notification read(User to) {
        Notification notification = unread(to);
        ReflectionTestUtils.setField(notification, "readAt", EARLIER_READ_AT);
        return notification;
    }

    private List<Notification> persistUnread(int count) {
        List<Notification> saved = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            saved.add(persist(unread(recipient)));
        }
        return saved;
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
