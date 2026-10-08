package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.ktb.moyeota.domain.chat.dto.MessageCursor;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final Long ROOM_ID = 501L;
    private static final Long USER_ID = 7L;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    private ChatMessageService service;

    private User sender;
    private ChatRoom chatRoom;
    private CompanionParticipant participant;

    @BeforeEach
    void setUp() {
        sender = user("우림");
        Companion companion = companionPost(sender);
        chatRoom = ChatRoom.create(companion);
        participant = CompanionParticipant.join(companion, sender);
        service = new ChatMessageService(messageRepository, companionParticipantRepository, new MessageCursorCodec(),
                new ImageUrlResolver(new S3Properties(
                        "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test")));
    }

    @Nested
    @DisplayName("참여자 검증")
    class ParticipantCheck {

        @Test
        @DisplayName("참여자가 아니면 CHATROOM_NOT_FOUND(존재 은닉)")
        void throwsWhenNotParticipant() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.findMessages(USER_ID, ROOM_ID, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHATROOM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("첫 진입(before, after 없음)")
    class FirstEntry {

        @Test
        @DisplayName("#1 메시지가 없으면 빈 목록과 두 커서 모두 null")
        void empty() {
            givenParticipant(null);
            givenRoomMessages(List.of());

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(response.items()).isEmpty();
            assertThat(response.beforeCursor()).isNull();
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#2 한 번도 읽지 않았고 전체가 20개 이하면 전부, 두 커서 모두 null")
        void neverReadUnderPageSize() {
            givenParticipant(null);
            givenRoomMessages(range(1, 15));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(1, 15));
            assertThat(response.beforeCursor()).isNull();
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#3 안 읽은 메시지가 없고 전체가 20개 초과면 최신 20개, before_cursor만 있다")
        void noUnreadOverPageSize() {
            givenParticipant(60L);
            givenRoomMessages(range(1, 60));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(41, 60));
            assertThat(cursorId(response.beforeCursor())).isEqualTo(41L);
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#4 안 읽음 있음, 이전 10개 이하 · 이후 10개 이하면 전부, 두 커서 모두 null")
        void unreadBothSidesShort() {
            givenParticipant(5L);
            givenRoomMessages(range(1, 12));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(1, 12));
            assertThat(response.beforeCursor()).isNull();
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#5 안 읽음 있음, 이전 10개 이하 · 이후 10개 초과면 after_cursor만 있다")
        void unreadNewerSideLong() {
            givenParticipant(5L);
            givenRoomMessages(range(1, 30));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(1, 15));
            assertThat(response.beforeCursor()).isNull();
            assertThat(cursorId(response.afterCursor())).isEqualTo(15L);
        }

        @Test
        @DisplayName("#6 안 읽음 있음, 이전 10개 초과 · 이후 10개 이하면 before_cursor만 있다")
        void unreadOlderSideLong() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 35));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(21, 35));
            assertThat(cursorId(response.beforeCursor())).isEqualTo(21L);
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#7 안 읽음 있음, 양쪽 모두 10개 초과면 last_read 포함 이전 10개 + 이후 10개, 두 커서 모두 있다")
        void unreadBothSidesLong() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 60));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(21, 40));
            assertThat(cursorId(response.beforeCursor())).isEqualTo(21L);
            assertThat(cursorId(response.afterCursor())).isEqualTo(40L);
        }

        @Test
        @DisplayName("방금 보낸 내 메시지처럼 last_read 이후 메시지가 있으면 목록에 포함된다")
        void includesMessagesRightAfterLastRead() {
            givenParticipant(100L);
            givenRoomMessages(List.of(98L, 99L, 100L, 101L));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactly(101L, 100L, 99L, 98L);
        }

        @Test
        @DisplayName("id가 연속되지 않아도(다른 방 메시지가 끼어 있어도) last_read 다음 메시지부터 포함한다")
        void nonContiguousIds() {
            givenParticipant(100L);
            givenRoomMessages(List.of(90L, 100L, 107L, 115L));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(ids(response)).containsExactly(115L, 107L, 100L, 90L);
        }
    }

    @Nested
    @DisplayName("위로 스크롤(before)")
    class Before {

        @Test
        @DisplayName("#8 남은 과거가 20개 이하면 전부, 두 커서 모두 null")
        void remainingUnderPageSize() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 60));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "before", cursor(21L), null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(1, 20));
            assertThat(response.beforeCursor()).isNull();
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#9 남은 과거가 20개 초과면 20개, before_cursor만 있다")
        void remainingOverPageSize() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 60));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "before", cursor(41L), null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(21, 40));
            assertThat(cursorId(response.beforeCursor())).isEqualTo(21L);
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("before 커서가 있으면 last_read는 쓰지 않는다")
        void ignoresLastRead() {
            givenParticipant(999L);
            givenRoomMessages(range(1, 60));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "before", cursor(11L), null);

            assertThat(ids(response)).containsExactlyElementsOf(desc(1, 10));
        }
    }

    @Nested
    @DisplayName("반대 방향 커서 유지")
    class KeepOppositeCursor {

        @Test
        @DisplayName("before 요청이면 함께 받은 after 커서를 그대로 돌려준다")
        void beforeKeepsAfter() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 60));
            String after = cursor(40L);

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "before", cursor(21L), after);

            assertThat(ids(response)).containsExactlyElementsOf(desc(1, 20));
            assertThat(response.beforeCursor()).isNull();
            assertThat(response.afterCursor()).isEqualTo(after);
        }

        @Test
        @DisplayName("after 요청이면 함께 받은 before 커서를 그대로 돌려준다")
        void afterKeepsBefore() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 52));
            String before = cursor(21L);

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "after", before, cursor(40L));

            assertThat(ids(response)).containsExactlyElementsOf(desc(41, 52));
            assertThat(response.beforeCursor()).isEqualTo(before);
            assertThat(response.afterCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("아래로 스크롤(after)")
    class After {

        @Test
        @DisplayName("#10 남은 최신이 20개 이하면 전부(id 내림차순), 두 커서 모두 null")
        void remainingUnderPageSize() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 52));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "after", null, cursor(40L));

            assertThat(ids(response)).containsExactlyElementsOf(desc(41, 52));
            assertThat(response.beforeCursor()).isNull();
            assertThat(response.afterCursor()).isNull();
        }

        @Test
        @DisplayName("#11 남은 최신이 20개 초과면 커서에 가까운 20개(id 내림차순), after_cursor만 있다")
        void remainingOverPageSize() {
            givenParticipant(30L);
            givenRoomMessages(range(1, 100));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, "after", null, cursor(40L));

            assertThat(ids(response)).containsExactlyElementsOf(desc(41, 60));
            assertThat(response.beforeCursor()).isNull();
            assertThat(cursorId(response.afterCursor())).isEqualTo(60L);
        }
    }

    @Nested
    @DisplayName("잘못된 요청")
    class InvalidRequest {

        @Test
        @DisplayName("direction 없이 커서를 보내면 INVALID_DIRECTION")
        void cursorWithoutDirection() {
            givenParticipant(null);

            assertError(() -> service.findMessages(USER_ID, ROOM_ID, null, cursor(10L), null),
                    ChatErrorCode.INVALID_DIRECTION);
        }

        @Test
        @DisplayName("direction 값이 before/after가 아니면 INVALID_DIRECTION")
        void unknownDirection() {
            givenParticipant(null);

            assertError(() -> service.findMessages(USER_ID, ROOM_ID, "up", cursor(10L), null),
                    ChatErrorCode.INVALID_DIRECTION);
        }

        @Test
        @DisplayName("direction=before인데 before 커서가 없으면 INVALID_CURSOR")
        void missingRequiredCursor() {
            givenParticipant(null);

            assertError(() -> service.findMessages(USER_ID, ROOM_ID, "before", null, cursor(10L)),
                    CommonErrorCode.INVALID_CURSOR);
        }

        @Test
        @DisplayName("형식이 잘못된 커서면 INVALID_CURSOR")
        void malformed() {
            givenParticipant(null);

            assertError(() -> service.findMessages(USER_ID, ROOM_ID, "before", "abc", null),
                    CommonErrorCode.INVALID_CURSOR);
        }

        @Test
        @DisplayName("함께 보낸 반대 방향 커서의 형식이 잘못돼도 INVALID_CURSOR")
        void malformedOpposite() {
            givenParticipant(null);

            assertError(() -> service.findMessages(USER_ID, ROOM_ID, "before", cursor(10L), "abc"),
                    CommonErrorCode.INVALID_CURSOR);
        }

        @Test
        @DisplayName("id가 없는 커서(예: 이전 형식 {\"lastId\":..})면 INVALID_CURSOR")
        void missingId() {
            givenParticipant(null);
            String legacy = "v1." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"lastId\":10}".getBytes(StandardCharsets.UTF_8));

            assertError(() -> service.findMessages(USER_ID, ROOM_ID, "before", legacy, null),
                    CommonErrorCode.INVALID_CURSOR);
        }

        private void assertError(ThrowingCallable call, ErrorCode code) {
            assertThatThrownBy(call)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(code);
        }
    }

    @Nested
    @DisplayName("발신자 프로필 이미지")
    class SenderProfileImage {

        @Test
        @DisplayName("저장된 이미지 키를 URL로 바꿔 내린다")
        void resolvesKeyToUrl() {
            sender.changeProfileImage("profile/a.png");
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                    .willReturn(List.of(message(1L)));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(response.items().get(0).sender().profileImageUrl())
                    .isEqualTo("https://cdn.moyeota.test/profile/a.png");
        }

        @Test
        @DisplayName("이미지가 없으면 null이고, 발신자가 없는 시스템 메시지도 함께 조회된다")
        void noImageAndSystemMessage() {
            Message rideStarted = Message.rideStartedSystemMessage(chatRoom, "system-key", null);
            ReflectionTestUtils.setField(rideStarted, "id", 2L);
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                    .willReturn(List.of(rideStarted, message(1L)));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null, null, null);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).sender()).isNull();
            assertThat(response.items().get(1).sender().profileImageUrl()).isNull();
        }
    }

    private void givenParticipant(Long lastReadMessageId) {
        if (lastReadMessageId != null) {
            ReflectionTestUtils.setField(participant, "message", message(lastReadMessageId));
        }
        given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(participant));
    }

    // 방에 있는 메시지 id 목록으로 두 조회 쿼리(id < cursor DESC / id > cursor ASC)를 흉내 낸다
    private void givenRoomMessages(List<Long> roomIds) {
        List<Long> sorted = roomIds.stream().sorted().toList();
        lenient().when(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Long cursor = invocation.getArgument(1);
                    Pageable pageable = invocation.getArgument(2);
                    return sorted.stream()
                            .filter(id -> cursor == null || id < cursor)
                            .sorted(Comparator.reverseOrder())
                            .limit(pageable.getPageSize())
                            .map(this::message)
                            .toList();
                });
        lenient().when(messageRepository.findNewerByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Long cursor = invocation.getArgument(1);
                    Pageable pageable = invocation.getArgument(2);
                    return sorted.stream()
                            .filter(id -> id > cursor)
                            .limit(pageable.getPageSize())
                            .map(this::message)
                            .toList();
                });
    }

    private String cursor(long id) {
        return new MessageCursorCodec().encode(new MessageCursor(id));
    }

    private Long cursorId(String cursor) {
        return new MessageCursorCodec().decode(cursor).id();
    }

    private static List<Long> ids(MessageListResponse response) {
        return response.items().stream().map(MessageItem::id).toList();
    }

    private static List<Long> range(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }

    private static List<Long> desc(long from, long to) {
        return LongStream.rangeClosed(from, to).map(i -> to - (i - from)).boxed().toList();
    }

    private Message message(long id) {
        Message message = Message.createGeneralMessage(chatRoom, sender, String.valueOf(id), "content-" + id);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
