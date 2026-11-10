package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.domain.user.entity.OwnedCar;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarpoolRideStartTest extends CarpoolRideTestSupport {

    @Test
    @DisplayName("방장이 운행을 시작하면 운행 중이 되고 도착 예정 시각이 시작 1시간 뒤로 저장된다")
    void startsRide() {
        Companion carpool = persistCarpool(RECRUITING, 2);

        CarpoolDetail detail = carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), IN_PROGRESS);

        assertThat(detail.status()).isEqualTo(IN_PROGRESS);
        Companion saved = reload(carpool);
        assertThat(saved.getStatus()).isEqualTo(IN_PROGRESS);
        assertThat(saved.getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    @Test
    @DisplayName("운행을 시작하면 채팅방에 운행 시작 알림이 남고 방은 열려 있다")
    void leavesRideStartedMessage() {
        Companion carpool = persistCarpool(RECRUITING, 2);

        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), IN_PROGRESS);

        assertThat(messageTypes(carpool)).containsExactly(MessageType.SYSTEM_RIDE_STARTED);
        assertThat(reloadChatRoom(carpool).getClosedAt()).isNull();
    }

    @Test
    @DisplayName("운행을 시작하면 시작 시각과 경로를 담아 운행 시작 이벤트를 발행한다")
    void publishesRideStarted() {
        Companion carpool = persistCarpool(RECRUITING, 2);

        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), IN_PROGRESS);

        assertThat(events.stream(CarpoolRideStartedEvent.class)).containsExactly(new CarpoolRideStartedEvent(
                carpool.getId(), DEPARTURE_AT, ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG));
    }

    @Test
    @DisplayName("혼자면 운행을 시작하지 못하고 알림도 남지 않는다")
    void rejectsStartAlone() {
        Companion carpool = persistCarpool(RECRUITING, 1);

        assertErrorCode(() -> carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), IN_PROGRESS),
                CompanionErrorCode.NOT_ENOUGH_PARTICIPANTS);
        assertThat(messageTypes(carpool)).isEmpty();
    }

    @Test
    @DisplayName("응답에는 실명으로 방장 · 참여 순서대로의 참여자와 방장 차종, 정원 여부가 담긴다")
    void returnsDetail() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        User host = carpool.getHost();
        entityManager.persistAndFlush(OwnedCar.register(host, "포르쉐 911", "12가3456"));

        CarpoolDetail detail = carpoolRideService.changeStatus(host.getId(), carpool.getId(), IN_PROGRESS);

        assertThat(detail.host()).isEqualTo(new Member(host.getId(), "김방장", null));
        assertThat(detail.carModel()).isEqualTo("포르쉐 911");
        assertThat(detail.participants()).extracting(Member::name).containsExactly("김방장", "이동승");
        assertThat(detail.currentCount()).isEqualTo(2);
        assertThat(detail.capacity()).isEqualTo(4);
        assertThat(detail.full()).isFalse();
    }

    @Test
    @DisplayName("운행 중인 카풀은 받은 도착 예정 시각으로 바뀐다")
    void estimatesArrival() {
        Companion carpool = persistCarpool(IN_PROGRESS, 2);

        carpoolRideService.estimateArrival(carpool.getId(), DEPARTURE_AT.plusMinutes(20));

        assertThat(reload(carpool).getEtaAt()).isEqualTo(DEPARTURE_AT.plusMinutes(20));
    }
}
