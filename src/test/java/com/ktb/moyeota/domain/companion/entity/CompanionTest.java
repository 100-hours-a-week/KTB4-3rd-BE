package com.ktb.moyeota.domain.companion.entity;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_NAME;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_NAME;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompanionTest {

    @Nested
    @DisplayName("택시팟 열기")
    class OpenTaxiPot {

        @Test
        @DisplayName("연 사람이 방장인 4인 택시팟이 모집 중으로 열리고 인원은 1명이다")
        void opens() {
            User host = user("방장");

            Companion pot = Companion.openTaxiPot(
                    host, ORIGIN_NAME, ORIGIN_LAT, ORIGIN_LNG, DEST_NAME, DEST_LAT, DEST_LNG, DEPARTURE_AT);

            assertThat(pot.getKind()).isEqualTo(CompanionKind.TAXI_POT);
            assertThat(pot.getTransportType()).isEqualTo(TransportType.TAXI);
            assertThat(pot.getStatus()).isEqualTo(RECRUITING);
            assertThat(pot.getCapacity()).isEqualTo(4);
            assertThat(pot.getCurrentCount()).isEqualTo(1);
            assertThat(pot.getHost()).isSameAs(host);
            assertThat(pot.getCreator()).isSameAs(host);
        }
    }

    @Nested
    @DisplayName("합류")
    class Join {

        @Test
        @DisplayName("합류하면 인원이 1 늘고 진행 중인 참여 기록이 생긴다")
        void joins() {
            Companion pot = taxiPot(user("방장"), RECRUITING, 2);
            User joiner = user("합류자");

            CompanionParticipant participant = pot.join(joiner);

            assertThat(pot.getCurrentCount()).isEqualTo(3);
            assertThat(participant.getUser()).isSameAs(joiner);
            assertThat(participant.getOutcomeStatus()).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("정원이 찼으면 합류할 수 없다")
        void full() {
            Companion pot = taxiPot(user("방장"), RECRUITING, 4);

            assertThatThrownBy(() -> pot.join(user("합류자"))).isInstanceOf(IllegalStateException.class);
            assertThat(pot.getCurrentCount()).isEqualTo(4);
        }

        @ParameterizedTest(name = "{0}", quoteTextArguments = false)
        @EnumSource(value = CompanionStatus.class, names = "RECRUITING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("모집 중이 아니면 합류할 수 없다")
        void notRecruiting(CompanionStatus status) {
            Companion pot = taxiPot(user("방장"), status, 2);

            assertThatThrownBy(() -> pot.join(user("합류자"))).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("운행 시작")
    class StartRide {

        @Test
        @DisplayName("모집 중이고 2명 이상이며 출발 시각이 되면 운행 중이 된다")
        void starts() {
            Companion pot = taxiPot(user("방장"), RECRUITING, 2);

            pot.startRide(DEPARTURE_AT);

            assertThat(pot.getStatus()).isEqualTo(IN_PROGRESS);
        }

        @ParameterizedTest(name = "{0}", quoteTextArguments = false)
        @EnumSource(value = CompanionStatus.class, names = "RECRUITING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("모집 중이 아니면 INVALID_STATE_TRANSITION이다")
        void notRecruiting(CompanionStatus status) {
            Companion pot = taxiPot(user("방장"), status, 2);

            assertErrorCode(() -> pot.startRide(DEPARTURE_AT), CompanionErrorCode.INVALID_STATE_TRANSITION);
            assertThat(pot.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("혼자면 NOT_ENOUGH_PARTICIPANTS다")
        void alone() {
            Companion pot = taxiPot(user("방장"), RECRUITING, 1);

            assertErrorCode(() -> pot.startRide(DEPARTURE_AT), CompanionErrorCode.NOT_ENOUGH_PARTICIPANTS);
        }

        @Test
        @DisplayName("출발 시각 전이면 DEPARTURE_NOT_REACHED다")
        void beforeDeparture() {
            Companion pot = taxiPot(user("방장"), RECRUITING, 2);

            assertErrorCode(() -> pot.startRide(DEPARTURE_AT.minusSeconds(1)),
                    CompanionErrorCode.DEPARTURE_NOT_REACHED);
        }

        @Test
        @DisplayName("상태를 인원보다 먼저 본다")
        void checksStatusFirst() {
            Companion pot = taxiPot(user("방장"), IN_PROGRESS, 1);

            assertErrorCode(() -> pot.startRide(DEPARTURE_AT), CompanionErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    @Nested
    @DisplayName("운행 종료")
    class CompleteRide {

        @Test
        @DisplayName("운행 중이면 완료된다")
        void completes() {
            Companion pot = taxiPot(user("방장"), IN_PROGRESS, 2);

            pot.completeRide();

            assertThat(pot.getStatus()).isEqualTo(COMPLETED);
        }

        @ParameterizedTest(name = "{0}", quoteTextArguments = false)
        @EnumSource(value = CompanionStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("운행 중이 아니면 INVALID_STATE_TRANSITION이다")
        void notInProgress(CompanionStatus status) {
            Companion pot = taxiPot(user("방장"), status, 2);

            assertErrorCode(pot::completeRide, CompanionErrorCode.INVALID_STATE_TRANSITION);
            assertThat(pot.getStatus()).isEqualTo(status);
        }
    }

    private static void assertErrorCode(ThrowingCallable call, CompanionErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
