package com.ktb.moyeota.domain.carpool.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompanionRequestTest {

    @ParameterizedTest
    @EnumSource(RejectReason.class)
    @DisplayName("reject(reason): REJECTED 가 되고 거절 사유가 남는다")
    void rejectsWithReason(RejectReason reason) {
        CompanionRequest request = pending();

        request.reject(reason);

        assertThat(request.getStatus()).isEqualTo(CompanionRequestStatus.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("accept: 거절 사유는 비어 있다")
    void acceptHasNoReason() {
        CompanionRequest request = pending();

        request.accept();

        assertThat(request.getStatus()).isEqualTo(CompanionRequestStatus.ACCEPTED);
        assertThat(request.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("이미 처리된 요청을 거절하면 REQUEST_ALREADY_HANDLED 이고 사유는 바뀌지 않는다")
    void alreadyHandled() {
        CompanionRequest accepted = pending();
        accepted.accept();
        CompanionRequest rejected = pending();
        rejected.reject(RejectReason.HOST);

        assertThatThrownBy(() -> accepted.reject(RejectReason.CAPACITY_FULL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CarpoolErrorCode.REQUEST_ALREADY_HANDLED);
        assertThatThrownBy(() -> rejected.reject(RejectReason.DEPARTED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CarpoolErrorCode.REQUEST_ALREADY_HANDLED);
        assertThat(accepted.getRejectReason()).isNull();
        assertThat(rejected.getRejectReason()).isEqualTo(RejectReason.HOST);
    }

    @Test
    @DisplayName("거절 사유가 null 이면 예외이고 요청은 PENDING 그대로다")
    void nullReason() {
        CompanionRequest request = pending();

        assertThatThrownBy(() -> request.reject(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(request.getStatus()).isEqualTo(CompanionRequestStatus.PENDING);
        assertThat(request.getRejectReason()).isNull();
    }

    private static CompanionRequest pending() {
        return CompanionRequest.send(mock(Companion.class), mock(User.class), "같이 가요");
    }
}
