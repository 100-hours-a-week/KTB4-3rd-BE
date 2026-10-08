package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarpoolLeaveTest extends CarpoolRideTestSupport {

    @Test
    @DisplayName("동승자가 나가면 참여가 끝나고 인원이 줄며 퇴장 메시지가 남고 채팅방은 열려 있다")
    void memberLeaves() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        User member = memberOf(carpool);

        carpoolRideService.leave(member.getId(), carpool.getId());

        entityManager.flush();
        CompanionParticipant left = participantOf(carpool, member);
        assertThat(left.getOutcomeStatus()).isEqualTo(INCOMPLETE);
        assertThat(left.getLeftAt()).isNotNull();
        assertThat(messageTypes(carpool)).containsExactly(MessageType.SYSTEM_LEAVE);
        assertThat(reloadChatRoom(carpool).getClosedAt()).isNull();
        Companion reloaded = reload(carpool);
        assertThat(reloaded.getCurrentCount()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(RECRUITING);
    }

    @Test
    @DisplayName("방장은 모집 중에 나갈 수 없고 아무것도 바뀌지 않는다")
    void rejectsHost() {
        Companion carpool = persistCarpool(RECRUITING, 2);

        assertErrorCode(() -> carpoolRideService.leave(carpool.getHost().getId(), carpool.getId()),
                CarpoolErrorCode.HOST_CANNOT_LEAVE);

        assertThat(participantOf(carpool, carpool.getHost()).getOutcomeStatus()).isEqualTo(PENDING);
        assertThat(messageTypes(carpool)).isEmpty();
        assertThat(reload(carpool).getCurrentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("운행 중에는 동승자도 나갈 수 없다")
    void rejectsMemberDuringRide() {
        Companion carpool = persistCarpool(IN_PROGRESS, 2);
        User member = memberOf(carpool);

        assertErrorCode(() -> carpoolRideService.leave(member.getId(), carpool.getId()),
                CompanionErrorCode.RIDE_IN_PROGRESS);

        assertThat(participantOf(carpool, member).getOutcomeStatus()).isEqualTo(PENDING);
        assertThat(reload(carpool).getCurrentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("운행이 끝난 뒤에는 방장 · 동승자 모두 나가도 아무것도 바뀌지 않는다")
    void noOpAfterCompletion() {
        Companion carpool = persistCarpool(IN_PROGRESS, 2);
        User member = memberOf(carpool);
        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), COMPLETED);
        entityManager.flush();

        carpoolRideService.leave(carpool.getHost().getId(), carpool.getId());
        carpoolRideService.leave(member.getId(), carpool.getId());

        assertThat(messageTypes(carpool)).containsExactly(MessageType.SYSTEM_RIDE_ENDED);
        assertThat(participantsOf(carpool)).allSatisfy(participant -> assertThat(participant.isCompleted()).isTrue());
        assertThat(reload(carpool).getCurrentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 나간 사람이 다시 나가면 참여 중인 카풀이 아니다")
    void rejectsAlreadyLeft() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        User member = memberOf(carpool);
        carpoolRideService.leave(member.getId(), carpool.getId());
        entityManager.flush();

        assertErrorCode(() -> carpoolRideService.leave(member.getId(), carpool.getId()),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
        assertThat(messageTypes(carpool)).containsExactly(MessageType.SYSTEM_LEAVE);
    }

    @Test
    @DisplayName("참여하지 않은 사용자에게는 카풀이 없는 것과 같다")
    void rejectsOutsider() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        User outsider = entityManager.persistAndFlush(user("외부인"));

        assertErrorCode(() -> carpoolRideService.leave(outsider.getId(), carpool.getId()),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
    }

    @Test
    @DisplayName("택시팟 id 로는 카풀을 나가지 못한다")
    void rejectsTaxiPot() {
        User host = entityManager.persist(user("택시방장"));
        User member = entityManager.persist(user("택시동승"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, member, PENDING));
        entityManager.flush();

        assertErrorCode(() -> carpoolRideService.leave(member.getId(), pot.getId()),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
    }

    private User memberOf(Companion carpool) {
        return participantsOf(carpool).stream()
                .map(CompanionParticipant::getUser)
                .filter(user -> !carpool.isHostedBy(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    private CompanionParticipant participantOf(Companion carpool, User user) {
        return participantsOf(carpool).stream()
                .filter(participant -> participant.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }
}
