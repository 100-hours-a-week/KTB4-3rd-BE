package com.ktb.moyeota.domain.carpool.model;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MyRequestStatusTest {

    private static final LocalDateTime BEFORE_DEPARTURE = DEPARTURE_AT.minusMinutes(1);

    @Test
    @DisplayName("모집 중이고 출발 전인 카풀의 대기 요청은 대기 중이다. 출발 시각과 같은 순간까지 대기 중이다")
    void pending() {
        Companion carpool = carpool(user("방장"), CompanionStatus.RECRUITING, 1);

        assertThat(MyRequestStatus.of(CompanionRequestStatus.PENDING, carpool, BEFORE_DEPARTURE))
                .isEqualTo(MyRequestStatus.PENDING);
        assertThat(MyRequestStatus.of(CompanionRequestStatus.PENDING, carpool, DEPARTURE_AT))
                .isEqualTo(MyRequestStatus.PENDING);
    }

    @Test
    @DisplayName("출발 시각이 지난 모집 중 카풀의 대기 요청은 만료다")
    void expiredByDeparture() {
        Companion carpool = carpool(user("방장"), CompanionStatus.RECRUITING, 1);

        assertThat(MyRequestStatus.of(CompanionRequestStatus.PENDING, carpool, DEPARTURE_AT.plusMinutes(1)))
                .isEqualTo(MyRequestStatus.EXPIRED);
    }

    @ParameterizedTest
    @EnumSource(value = CompanionStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELED"})
    @DisplayName("모집이 끝난 카풀의 대기 요청은 출발 전이어도 만료다")
    void expiredByStatus(CompanionStatus status) {
        Companion carpool = carpool(user("방장"), status, 2);

        assertThat(MyRequestStatus.of(CompanionRequestStatus.PENDING, carpool, BEFORE_DEPARTURE))
                .isEqualTo(MyRequestStatus.EXPIRED);
    }

    @Test
    @DisplayName("수락 · 거절된 요청은 카풀이 끝나도 그대로다")
    void handledStaysAsIs() {
        Companion carpool = carpool(user("방장"), CompanionStatus.COMPLETED, 2);
        LocalDateTime afterDeparture = DEPARTURE_AT.plusHours(2);

        assertThat(MyRequestStatus.of(CompanionRequestStatus.ACCEPTED, carpool, afterDeparture))
                .isEqualTo(MyRequestStatus.ACCEPTED);
        assertThat(MyRequestStatus.of(CompanionRequestStatus.REJECTED, carpool, afterDeparture))
                .isEqualTo(MyRequestStatus.REJECTED);
    }
}
