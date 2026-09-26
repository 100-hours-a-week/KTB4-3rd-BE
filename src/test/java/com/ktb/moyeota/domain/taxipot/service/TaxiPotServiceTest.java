package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotStartCommand;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TaxiPotServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long TAXI_POT_ID = 30L;

    @Mock
    private TaxiPotRepository taxiPotRepository;

    @Mock
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private Clock clock = Clock.fixed(DEPARTURE_AT.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private TaxiPotService service;

    @Nested
    @DisplayName("매칭 시작")
    class Start {

        @Test
        @DisplayName("출발 시각이 지났으면 DEPARTURE_TIME_PASSED다")
        void departurePassed() {
            assertErrorCode(() -> service.start(USER_ID, startCommand(DEPARTURE_AT.minusMinutes(1))),
                    TaxiPotErrorCode.DEPARTURE_TIME_PASSED);
        }

        @Test
        @DisplayName("출발 시각이 3시간보다 멀면 DEPARTURE_TIME_TOO_FAR다")
        void departureTooFar() {
            assertErrorCode(() -> service.start(USER_ID, startCommand(DEPARTURE_AT.plusHours(3).plusMinutes(1))),
                    TaxiPotErrorCode.DEPARTURE_TIME_TOO_FAR);
        }

        @Test
        @DisplayName("출발지와 도착지 좌표가 같으면 SAME_ORIGIN_DEST다")
        void sameOriginDest() {
            TaxiPotStartCommand sameRoute = new TaxiPotStartCommand(
                    "판교역", new BigDecimal("37.394500"), new BigDecimal("127.111200"),
                    "판교역 2번 출구", new BigDecimal("37.3945"), new BigDecimal("127.1112"),
                    DEPARTURE_AT);

            assertErrorCode(() -> service.start(USER_ID, sameRoute), TaxiPotErrorCode.SAME_ORIGIN_DEST);
        }

        @Test
        @DisplayName("정산 계좌가 없으면 BANK_ACCOUNT_REQUIRED다")
        void bankAccountRequired() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user(USER_ID, "계좌없음")));

            assertErrorCode(() -> service.start(USER_ID, startCommand(DEPARTURE_AT)),
                    TaxiPotErrorCode.BANK_ACCOUNT_REQUIRED);
        }

        @Test
        @DisplayName("진행 중인 택시팟 참여가 있으면 MATCH_ALREADY_IN_PROGRESS다")
        void alreadyInProgress() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(bankAccountHolder(USER_ID, "참여중")));
            given(taxiPotParticipantRepository.findCurrentTaxiPot(USER_ID))
                    .willReturn(Optional.of(taxiPot(user("방장"), RECRUITING)));

            assertErrorCode(() -> service.start(USER_ID, startCommand(DEPARTURE_AT)),
                    TaxiPotErrorCode.MATCH_ALREADY_IN_PROGRESS);
        }

        @Test
        @DisplayName("데드락으로 튕기면 새 트랜잭션으로 다시 시도한다")
        void retriesOnDeadlock() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(bankAccountHolder(USER_ID, "재시도")));
            given(taxiPotParticipantRepository.findCurrentTaxiPot(USER_ID)).willReturn(Optional.empty());
            given(taxiPotRepository.findMatchableTaxiPotForUpdate(any(), any(), any(), any(), any()))
                    .willThrow(new CannotAcquireLockException("Deadlock found"))
                    .willReturn(Optional.of(taxiPot(user("방장"), RECRUITING, 1)));

            CurrentTaxiPot joined = service.start(USER_ID, startCommand(DEPARTURE_AT));

            assertThat(joined.currentCount()).isEqualTo(2);
            verify(transactionTemplate, times(2)).execute(any());
        }

        @Test
        @DisplayName("세 번 모두 데드락이면 MATCH_BUSY다")
        void givesUpAfterThreeDeadlocks() {
            given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(bankAccountHolder(USER_ID, "포기")));
            given(taxiPotParticipantRepository.findCurrentTaxiPot(USER_ID)).willReturn(Optional.empty());
            given(taxiPotRepository.findMatchableTaxiPotForUpdate(any(), any(), any(), any(), any()))
                    .willThrow(new CannotAcquireLockException("Deadlock found"));

            assertErrorCode(() -> service.start(USER_ID, startCommand(DEPARTURE_AT)), TaxiPotErrorCode.MATCH_BUSY);
            verify(transactionTemplate, times(3)).execute(any());
        }
    }

    @Nested
    @DisplayName("진행 중인 택시팟 조회")
    class FindMyCurrent {

        @Test
        @DisplayName("진행 중인 택시팟이 있으면 조회된다")
        void findsCurrent() {
            given(taxiPotParticipantRepository.findCurrentTaxiPot(USER_ID))
                    .willReturn(Optional.of(taxiPot(user("길동이"), RECRUITING)));

            assertThat(service.findMyCurrent(USER_ID)).isPresent();
        }

        @Test
        @DisplayName("진행 중인 택시팟이 없으면 비어 있다")
        void emptyWhenNone() {
            given(taxiPotParticipantRepository.findCurrentTaxiPot(USER_ID)).willReturn(Optional.empty());

            assertThat(service.findMyCurrent(USER_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("택시팟 상세 조회")
    class Find {

        @Test
        @DisplayName("조회할 수 있는 택시팟이 없으면 TAXI_POT_NOT_FOUND다")
        void notFound() {
            given(taxiPotRepository.findTaxiPotForParticipant(TAXI_POT_ID, USER_ID)).willReturn(Optional.empty());

            assertErrorCode(() -> service.find(USER_ID, TAXI_POT_ID), TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("나가기")
    class Leave {

        @Test
        @DisplayName("참여 중인 택시팟이 아니면 TAXI_POT_NOT_FOUND다")
        void notFound() {
            given(taxiPotRepository.findTaxiPotForParticipantForUpdate(TAXI_POT_ID, USER_ID))
                    .willReturn(Optional.empty());

            assertErrorCode(() -> service.leave(USER_ID, TAXI_POT_ID), TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("운행 상태 변경")
    class ChangeStatus {

        private final User host = user(USER_ID, "방장");

        @Test
        @DisplayName("조회할 수 있는 택시팟이 없으면 TAXI_POT_NOT_FOUND다")
        void notFound() {
            given(taxiPotRepository.findTaxiPotForParticipantForUpdate(TAXI_POT_ID, USER_ID)).willReturn(Optional.empty());

            assertErrorCode(() -> service.changeStatus(USER_ID, TAXI_POT_ID, IN_PROGRESS),
                    TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
        }

        @Test
        @DisplayName("참여자지만 방장이 아니면 HOST_ONLY다")
        void hostOnly() {
            given(taxiPotRepository.findTaxiPotForParticipantForUpdate(TAXI_POT_ID, USER_ID))
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
