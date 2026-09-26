package com.ktb.moyeota.domain.chat.repository;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class CompanionParticipantRepositoryTest {

    @Autowired
    private CompanionParticipantRepository companionParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private Companion companion;

    @BeforeEach
    void setUp() {
        host = persist(user("방장"));
        companion = persist(companionPost(host));
    }

    @Test
    @DisplayName("같은 동행에 같은 사람의 참여 행은 하나뿐이다")
    void oneParticipationPerUser() {
        User guest = persist(user("게스트"));
        companionParticipantRepository.saveAndFlush(CompanionParticipant.join(companion, guest));

        assertThatThrownBy(() -> companionParticipantRepository.saveAndFlush(CompanionParticipant.join(companion, guest)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Nested
    @DisplayName("findActiveByChatRoomIdAndUserId")
    class FindActiveByChatRoomIdAndUserId {

        @Test
        @DisplayName("활성 참여자를 찾는다")
        void found() {
            ChatRoom chatRoom = persist(ChatRoom.create(companion));
            CompanionParticipant participant = persist(CompanionParticipant.join(companion, host));

            Optional<CompanionParticipant> result = companionParticipantRepository
                    .findActiveByChatRoomIdAndUserId(chatRoom.getId(), host.getId());

            assertThat(result).get().extracting(CompanionParticipant::getId).isEqualTo(participant.getId());
        }

        @Test
        @DisplayName("나간 참여자는 찾을 수 없다")
        void excludesLeft() {
            ChatRoom chatRoom = persist(ChatRoom.create(companion));
            CompanionParticipant participant = persist(CompanionParticipant.join(companion, host));
            participant.leave();
            persist(participant);

            assertThat(companionParticipantRepository
                    .findActiveByChatRoomIdAndUserId(chatRoom.getId(), host.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 채팅방의 참여자는 찾지 않는다")
        void isolatesByRoom() {
            persist(ChatRoom.create(companion));
            persist(CompanionParticipant.join(companion, host));

            Companion otherCompanion = persist(companionPost(persist(user("다른방장"))));
            ChatRoom otherRoom = persist(ChatRoom.create(otherCompanion));

            assertThat(companionParticipantRepository
                    .findActiveByChatRoomIdAndUserId(otherRoom.getId(), host.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateLastReadMessageIfNewer")
    class UpdateLastReadMessageIfNewer {

        private ChatRoom chatRoom;
        private CompanionParticipant participant;

        @BeforeEach
        void setUp() {
            chatRoom = persist(ChatRoom.create(companion));
            participant = persist(CompanionParticipant.join(companion, host));
        }

        @Test
        @DisplayName("읽은 메시지가 없으면(NULL) 갱신된다")
        void updatesWhenNeverRead() {
            Message message = persist(Message.createGeneralMessage(chatRoom, host, 1L, "hi"));

            int updated = companionParticipantRepository.updateLastReadMessageIfNewer(
                    participant.getId(), message, message.getId());

            assertThat(updated).isEqualTo(1);
            entityManager.clear();
            CompanionParticipant refetched = entityManager.find(CompanionParticipant.class, participant.getId());
            assertThat(refetched.getMessage().getId()).isEqualTo(message.getId());
        }

        @Test
        @DisplayName("더 최신 메시지면 갱신된다")
        void updatesWhenNewer() {
            Message first = persist(Message.createGeneralMessage(chatRoom, host, 1L, "1"));
            ReflectionTestUtils.setField(participant, "message", first);
            persist(participant);
            Message second = persist(Message.createGeneralMessage(chatRoom, host, 2L, "2"));

            int updated = companionParticipantRepository.updateLastReadMessageIfNewer(
                    participant.getId(), second, second.getId());

            assertThat(updated).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 더 최신을 읽은 상태면 갱신되지 않는다")
        void doesNotRegressToOlder() {
            // id는 persist 순서(자동 증가)로 매겨지므로, "이미 읽은 메시지"가 더 큰 id를 갖도록
            // older -> newer 순서로 먼저 persist한다.
            Message older = persist(Message.createGeneralMessage(chatRoom, host, 1L, "1"));
            Message newer = persist(Message.createGeneralMessage(chatRoom, host, 2L, "2"));
            ReflectionTestUtils.setField(participant, "message", newer);
            persist(participant);

            int updated = companionParticipantRepository.updateLastReadMessageIfNewer(
                    participant.getId(), older, older.getId());

            assertThat(updated).isZero();
        }
    }

    @Nested
    @DisplayName("findActiveByCompanionIdAndUserId")
    class FindActiveByCompanionIdAndUserId {

        @Test
        @DisplayName("활성 참여자를 찾는다")
        void found() {
            CompanionParticipant participant = persist(CompanionParticipant.join(companion, host));

            assertThat(companionParticipantRepository
                    .findActiveByCompanionIdAndUserId(companion.getId(), host.getId()))
                    .get().extracting(CompanionParticipant::getId).isEqualTo(participant.getId());
        }

        @Test
        @DisplayName("나간 참여자는 찾을 수 없다")
        void excludesLeft() {
            CompanionParticipant participant = persist(CompanionParticipant.join(companion, host));
            participant.leave();
            persist(participant);

            assertThat(companionParticipantRepository
                    .findActiveByCompanionIdAndUserId(companion.getId(), host.getId())).isEmpty();
        }

        @Test
        @DisplayName("참여한 적 없는 유저는 찾을 수 없다")
        void notParticipating() {
            User stranger = persist(user("모르는사람"));

            assertThat(companionParticipantRepository
                    .findActiveByCompanionIdAndUserId(companion.getId(), stranger.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc")
    class FindNextHost {

        @Test
        @DisplayName("본인을 제외하고 가장 먼저 합류한 활성 참여자를 찾는다")
        void findsEarliestOther() {
            User earlier = persist(user("먼저합류"));
            User later = persist(user("나중합류"));

            CompanionParticipant earlierParticipant = joinAt(earlier, LocalDateTime.of(2026, 9, 1, 0, 0));
            joinAt(later, LocalDateTime.of(2026, 9, 2, 0, 0));

            Optional<CompanionParticipant> result = companionParticipantRepository
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(
                            companion.getId(), host.getId());

            assertThat(result).get().extracting(CompanionParticipant::getId).isEqualTo(earlierParticipant.getId());
        }

        @Test
        @DisplayName("나간 참여자는 다음 방장 후보에서 제외된다")
        void excludesLeftParticipant() {
            User left = persist(user("나감"));
            CompanionParticipant leftParticipant = joinAt(left, LocalDateTime.of(2026, 9, 1, 0, 0));
            leftParticipant.leave();
            persist(leftParticipant);

            User active = persist(user("남아있음"));
            CompanionParticipant activeParticipant = joinAt(active, LocalDateTime.of(2026, 9, 2, 0, 0));

            Optional<CompanionParticipant> result = companionParticipantRepository
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(
                            companion.getId(), host.getId());

            assertThat(result).get().extracting(CompanionParticipant::getId).isEqualTo(activeParticipant.getId());
        }

        @Test
        @DisplayName("본인 외에 아무도 없으면 찾을 수 없다")
        void emptyWhenAlone() {
            assertThat(companionParticipantRepository
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(
                            companion.getId(), host.getId())).isEmpty();
        }

        private CompanionParticipant joinAt(User user, LocalDateTime joinedAt) {
            CompanionParticipant participant = CompanionParticipant.join(companion, user);
            ReflectionTestUtils.setField(participant, "joinedAt", joinedAt);
            return persist(participant);
        }
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
