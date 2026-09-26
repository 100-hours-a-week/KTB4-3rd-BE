package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.companion.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.repository.CompanionRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxiPotServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long TAXI_POT_ID = 30L;

    @Mock
    private CompanionRepository companionRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @InjectMocks
    private TaxiPotService service;

    @Nested
    @DisplayName("진행 중인 택시팟 조회")
    class FindMyCurrent {

        @Test
        @DisplayName("진행 중인 택시팟이 있으면 조회된다")
        void findsCurrent() {
            given(companionParticipantRepository.findCurrentTaxiPot(USER_ID))
                    .willReturn(Optional.of(taxiPot(user("길동이"), RECRUITING)));

            assertThat(service.findMyCurrent(USER_ID)).isPresent();
        }

        @Test
        @DisplayName("진행 중인 택시팟이 없으면 비어 있다")
        void emptyWhenNone() {
            given(companionParticipantRepository.findCurrentTaxiPot(USER_ID)).willReturn(Optional.empty());

            assertThat(service.findMyCurrent(USER_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("택시팟 상세 조회")
    class Find {

        @Test
        @DisplayName("조회할 수 있는 택시팟이 없으면 TAXI_POT_NOT_FOUND다")
        void notFound() {
            given(companionRepository.findTaxiPotForParticipant(TAXI_POT_ID, USER_ID)).willReturn(Optional.empty());

            assertErrorCode(() -> service.find(USER_ID, TAXI_POT_ID), TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("운행 상태 변경")
    class ChangeStatus {

        private final User host = user(USER_ID, "방장");

        @Test
        @DisplayName("조회할 수 있는 택시팟이 없으면 TAXI_POT_NOT_FOUND다")
        void notFound() {
            given(companionRepository.findTaxiPotForParticipantForUpdate(TAXI_POT_ID, USER_ID)).willReturn(Optional.empty());

            assertErrorCode(() -> service.changeStatus(USER_ID, TAXI_POT_ID, IN_PROGRESS),
                    TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
        }

        @Test
        @DisplayName("참여자지만 방장이 아니면 HOST_ONLY다")
        void hostOnly() {
            given(companionRepository.findTaxiPotForParticipantForUpdate(TAXI_POT_ID, USER_ID))
                    .willReturn(Optional.of(taxiPot(user(7L, "다른방장"), RECRUITING, 2)));

            assertErrorCode(() -> service.changeStatus(USER_ID, TAXI_POT_ID, IN_PROGRESS),
                    TaxiPotErrorCode.HOST_ONLY);
        }


    }

    private static void assertErrorCode(ThrowingCallable call, TaxiPotErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
