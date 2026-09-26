package com.ktb.moyeota.domain.chat.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionKind.COMPANION;
import static com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class ChatRoomRepositoryTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User host;

    @BeforeEach
    void setUp() {
        host = persist(user("방장"));
    }

    @Nested
    @DisplayName("findByCompanionId")
    class FindByCompanionId {

        @Test
        @DisplayName("동행에 연결된 채팅방을 찾는다")
        void found() {
            Companion companion = persist(companionPost(host));
            ChatRoom chatRoom = persist(ChatRoom.create(companion));

            assertThat(chatRoomRepository.findByCompanionId(companion.getId()))
                    .get().extracting(ChatRoom::getId).isEqualTo(chatRoom.getId());
        }

        @Test
        @DisplayName("연결된 채팅방이 없으면 찾을 수 없다")
        void notFound() {
            Companion companion = persist(companionPost(host));

            assertThat(chatRoomRepository.findByCompanionId(companion.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdWithCompanion")
    class FindByIdWithCompanion {

        @Test
        @DisplayName("채팅방과 동행을 함께 조회한다")
        void found() {
            Companion companion = persist(companionPost(host));
            ChatRoom chatRoom = persist(ChatRoom.create(companion));
            entityManager.clear();

            ChatRoom fetched = chatRoomRepository.findByIdWithCompanion(chatRoom.getId()).orElseThrow();

            assertThat(fetched.getCompanion().getId()).isEqualTo(companion.getId());
        }

        @Test
        @DisplayName("없는 id는 찾을 수 없다")
        void notFound() {
            assertThat(chatRoomRepository.findByIdWithCompanion(999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findMyChatRooms")
    class FindMyChatRooms {

        private User me;

        @BeforeEach
        void setUp() {
            me = persist(user("길동이"));
        }

        @Test
        @DisplayName("내가 활성 참여자인 채팅방만 조회된다")
        void onlyMine() {
            Companion mine = persist(companionPost(host));
            joinRoomWithMessage(mine, me, "hi");

            Companion others = persist(companionPost(persist(user("다른유저"))));
            joinRoomWithMessage(others, persist(user("다른참여자")), "hello");

            List<ChatRoomListProjection> result = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCompanionId()).isEqualTo(mine.getId());
        }

        @Test
        @DisplayName("나간 채팅방은 조회되지 않는다")
        void excludesLeftRoom() {
            Companion companion = persist(companionPost(host));
            CompanionParticipant participant = joinRoomWithMessage(companion, me, "hi").participant();
            participant.leave();
            entityManager.persistAndFlush(participant);

            List<ChatRoomListProjection> result = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("kind로 필터링할 수 있다")
        void filtersByKind() {
            Companion taxi = persist(taxiPot(host, RECRUITING));
            joinRoomWithMessage(taxi, me, "택시");

            Companion companion = persist(companionPost(persist(user("동행방장"))));
            joinRoomWithMessage(companion, me, "동행");

            List<ChatRoomListProjection> taxiOnly = chatRoomRepository.findMyChatRooms(
                    me.getId(), TAXI_POT.name(), null, null, 10);
            List<ChatRoomListProjection> companionOnly = chatRoomRepository.findMyChatRooms(
                    me.getId(), COMPANION.name(), null, null, 10);
            List<ChatRoomListProjection> all = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 10);

            assertThat(taxiOnly).hasSize(1);
            assertThat(taxiOnly.get(0).getCompanionId()).isEqualTo(taxi.getId());
            assertThat(companionOnly).hasSize(1);
            assertThat(companionOnly.get(0).getCompanionId()).isEqualTo(companion.getId());
            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("최근 메시지 시각 내림차순(동률이면 id 내림차순)으로 정렬된다")
        void ordersByLastMessageAtThenId() {
            Companion first = persist(companionPost(host));
            joinRoomWithMessage(first, me, "1번");

            Companion second = persist(companionPost(persist(user("두번째방장"))));
            joinRoomWithMessage(second, me, "2번");

            Companion third = persist(companionPost(persist(user("세번째방장"))));
            joinRoomWithMessage(third, me, "3번");

            List<ChatRoomListProjection> result = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 10);

            assertThat(result).extracting(ChatRoomListProjection::getCompanionId)
                    .containsExactly(third.getId(), second.getId(), first.getId());
        }

        @Test
        @DisplayName("커서 이후(더 오래된) 채팅방만 조회한다")
        void cursorPagination() {
            Companion first = persist(companionPost(host));
            joinRoomWithMessage(first, me, "1번");

            Companion second = persist(companionPost(persist(user("두번째방장"))));
            joinRoomWithMessage(second, me, "2번");

            Companion third = persist(companionPost(persist(user("세번째방장"))));
            joinRoomWithMessage(third, me, "3번");

            ChatRoomListProjection cursorRow = chatRoomRepository.findMyChatRooms(
                            me.getId(), null, null, null, 10).stream()
                    .filter(row -> row.getCompanionId().equals(second.getId()))
                    .findFirst().orElseThrow();

            List<ChatRoomListProjection> result = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, cursorRow.getLastMessageAt(), cursorRow.getId(), 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCompanionId()).isEqualTo(first.getId());
        }

        @Test
        @DisplayName("n(limit)만큼만 가져온다")
        void limitsSize() {
            for (int i = 0; i < 3; i++) {
                Companion companion = persist(companionPost(persist(user("방장" + i))));
                joinRoomWithMessage(companion, me, "메시지" + i);
            }

            List<ChatRoomListProjection> result = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 2);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("마지막 메시지·읽음 처리 여부가 함께 조회된다")
        void carriesReadState() {
            Companion companion = persist(companionPost(host));
            ChatRoomJoinResult joined = joinRoomWithMessage(companion, me, "안읽은 메시지");

            List<ChatRoomListProjection> unread = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 10);
            assertThat(unread.get(0).getLastMessageId()).isEqualTo(joined.message().getId());
            assertThat(unread.get(0).getLastReadMessageId()).isNull();

            CompanionParticipant participant = joined.participant();
            ReflectionTestUtils.setField(participant, "message", joined.message());
            entityManager.persistAndFlush(participant);

            List<ChatRoomListProjection> read = chatRoomRepository.findMyChatRooms(
                    me.getId(), null, null, null, 10);
            assertThat(read.get(0).getLastReadMessageId()).isEqualTo(joined.message().getId());
        }

        private ChatRoomJoinResult joinRoomWithMessage(Companion companion, User participantUser, String content) {
            ChatRoom chatRoom = persist(ChatRoom.create(companion));
            CompanionParticipant participant = persist(CompanionParticipant.join(companion, participantUser));
            Message message = persist(Message.createGeneralMessage(chatRoom, participantUser, 1L, content));
            chatRoom.updateLastMessageId(message.getId());
            entityManager.persistAndFlush(chatRoom);
            return new ChatRoomJoinResult(chatRoom, participant, message);
        }

        private record ChatRoomJoinResult(ChatRoom chatRoom, CompanionParticipant participant, Message message) {
        }
    }

    @Nested
    @DisplayName("정원 카운트 증감")
    class CapacityCounters {

        @Test
        @DisplayName("모집 중이고 자리가 있으면 증가한다")
        void increases() {
            Companion companion = persist(taxiPot(host, RECRUITING, 1));

            int updated = chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(
                    companion.getId(), CompanionStatus.RECRUITING);

            assertThat(updated).isEqualTo(1);
            entityManager.clear();
            assertThat(entityManager.find(Companion.class, companion.getId()).getCurrentCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("정원이 찼으면 증가하지 않는다")
        void doesNotIncreaseWhenFull() {
            Companion companion = persist(taxiPot(host, RECRUITING, 4));

            int updated = chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(
                    companion.getId(), CompanionStatus.RECRUITING);

            assertThat(updated).isZero();
        }

        @Test
        @DisplayName("모집 중이 아니면 증가하지 않는다")
        void doesNotIncreaseWhenNotRecruiting() {
            Companion companion = persist(taxiPot(host, CompanionStatus.IN_PROGRESS, 1));

            int updated = chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(
                    companion.getId(), CompanionStatus.RECRUITING);

            assertThat(updated).isZero();
        }

        @Test
        @DisplayName("인원이 있으면 감소한다")
        void decreases() {
            Companion companion = persist(taxiPot(host, RECRUITING, 2));

            int updated = chatRoomRepository.decreaseCurrentCount(companion.getId());

            assertThat(updated).isEqualTo(1);
            entityManager.clear();
            assertThat(entityManager.find(Companion.class, companion.getId()).getCurrentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("인원이 0이면 감소하지 않는다")
        void doesNotGoNegative() {
            Companion companion = persist(taxiPot(host, RECRUITING, 0));

            int updated = chatRoomRepository.decreaseCurrentCount(companion.getId());

            assertThat(updated).isZero();
        }
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
