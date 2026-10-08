package com.ktb.moyeota.domain.carpool.service;

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

class CarpoolRideStatusTest extends CarpoolRideTestSupport {

    @Test
    @DisplayName("방장이 운행을 종료하면 함께 탄 참여자가 모두 완주하고 채팅방이 닫힌다")
    void completesRide() {
        Companion carpool = persistCarpool(IN_PROGRESS, 2);

        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), COMPLETED);

        entityManager.flush();
        assertThat(reload(carpool).getStatus()).isEqualTo(COMPLETED);
        assertThat(participantsOf(carpool)).hasSize(2).allSatisfy(participant -> {
            assertThat(participant.isCompleted()).isTrue();
            assertThat(participant.getLeftAt()).isNotNull();
        });
        assertThat(messageTypes(carpool)).containsExactly(MessageType.SYSTEM_RIDE_ENDED);
        assertThat(reloadChatRoom(carpool).getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("모집 중인 카풀을 바로 종료할 수는 없다")
    void rejectsCompleteBeforeStart() {
        Companion carpool = persistCarpool(RECRUITING, 2);

        assertErrorCode(() -> carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), COMPLETED),
                CompanionErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("방장이 아닌 참여자는 운행 상태를 바꾸지 못한다")
    void rejectsMember() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        User member = participantsOf(carpool).stream()
                .map(CompanionParticipant::getUser)
                .filter(user -> !carpool.isHostedBy(user.getId()))
                .findFirst()
                .orElseThrow();

        assertErrorCode(() -> carpoolRideService.changeStatus(member.getId(), carpool.getId(), IN_PROGRESS),
                CarpoolErrorCode.HOST_ONLY);
        assertThat(reload(carpool).getStatus()).isEqualTo(RECRUITING);
    }

    @Test
    @DisplayName("참여하지 않은 사용자에게는 카풀이 없는 것과 같다")
    void rejectsOutsider() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        User outsider = entityManager.persistAndFlush(user("외부인"));

        assertErrorCode(() -> carpoolRideService.changeStatus(outsider.getId(), carpool.getId(), IN_PROGRESS),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
    }

    @Test
    @DisplayName("택시팟 id 로는 카풀 운행 상태를 바꾸지 못한다")
    void rejectsTaxiPot() {
        User host = entityManager.persist(user("택시방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));

        assertErrorCode(() -> carpoolRideService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
    }
}
