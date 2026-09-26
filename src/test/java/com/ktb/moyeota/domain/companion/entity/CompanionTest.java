package com.ktb.moyeota.domain.companion.entity;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompanionTest {

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
