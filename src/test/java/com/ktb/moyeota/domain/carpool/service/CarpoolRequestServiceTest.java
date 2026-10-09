package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.SentJoinRequest;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({CarpoolRequestService.class, ChatSystemMessageService.class, CarpoolRequestServiceTest.Config.class})
class CarpoolRequestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private CarpoolRequestService carpoolRequestService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User requester;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
        requester = entityManager.persist(user("요청자"));
    }

    @Test
    @DisplayName("모집 중인 카풀에 요청을 보내면 대기 상태로 저장된다")
    void sends() {
        Companion carpool = persistCarpool(RECRUITING, 1);

        SentJoinRequest sent = carpoolRequestService.send(requester.getId(), carpool.getId(), "판교역에서 같이 가고 싶습니다!");

        assertThat(requestsOf(carpool)).singleElement().satisfies(request -> {
            assertThat(request.getId()).isEqualTo(sent.id());
            assertThat(request.getRequester().getId()).isEqualTo(requester.getId());
            assertThat(request.getStatus()).isEqualTo(CompanionRequestStatus.PENDING);
            assertThat(request.getContent()).isEqualTo("판교역에서 같이 가고 싶습니다!");
        });
        assertThat(sent.carpoolId()).isEqualTo(carpool.getId());
        assertThat(sent.status()).isEqualTo(CompanionRequestStatus.PENDING);
        assertThat(sent.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("본인 카풀에는 요청할 수 없다")
    void rejectsOwnCarpool() {
        Companion carpool = persistCarpool(RECRUITING, 1);

        assertRejected(() -> carpoolRequestService.send(host.getId(), carpool.getId(), "요청"),
                CarpoolErrorCode.OWN_CARPOOL, carpool);
    }

    @ParameterizedTest
    @EnumSource(value = CompanionStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELED"})
    @DisplayName("모집 중이 아닌 카풀은 마감이다")
    void rejectsNotRecruiting(CompanionStatus status) {
        Companion carpool = persistCarpool(status, 1);

        assertRejected(() -> carpoolRequestService.send(requester.getId(), carpool.getId(), "요청"),
                CarpoolErrorCode.CARPOOL_CLOSED, carpool);
    }

    @Test
    @DisplayName("출발 시각과 같은 순간까지는 요청하고, 지나면 마감이다")
    void departureBoundary() {
        Companion onTime = persistCarpool(RECRUITING, 1);
        ReflectionTestUtils.setField(onTime, "departureAt", NOW);
        Companion departed = persistCarpool(RECRUITING, 1);
        ReflectionTestUtils.setField(departed, "departureAt", NOW.minusMinutes(1));
        entityManager.flush();

        carpoolRequestService.send(requester.getId(), onTime.getId(), "요청");

        assertThat(requestsOf(onTime)).hasSize(1);
        assertRejected(() -> carpoolRequestService.send(requester.getId(), departed.getId(), "요청"),
                CarpoolErrorCode.CARPOOL_CLOSED, departed);
    }

    @Test
    @DisplayName("정원이 찬 카풀에는 요청할 수 없다")
    void rejectsFull() {
        Companion carpool = persistCarpool(RECRUITING, 4);

        assertRejected(() -> carpoolRequestService.send(requester.getId(), carpool.getId(), "요청"),
                CarpoolErrorCode.CAPACITY_FULL, carpool);
    }

    @Test
    @DisplayName("이미 타고 있으면 다시 요청할 수 없고, 나간 사람은 다시 요청할 수 있다")
    void participation() {
        Companion riding = persistCarpool(RECRUITING, 2);
        entityManager.persist(participant(riding, requester, OutcomeStatus.PENDING));
        Companion left = persistCarpool(RECRUITING, 1);
        entityManager.persist(participant(left, requester, OutcomeStatus.INCOMPLETE));
        entityManager.flush();

        assertRejected(() -> carpoolRequestService.send(requester.getId(), riding.getId(), "요청"),
                CarpoolErrorCode.ALREADY_PARTICIPATING, riding);
        carpoolRequestService.send(requester.getId(), left.getId(), "다시 타고 싶어요");
        assertThat(requestsOf(left)).hasSize(1);
    }

    @Test
    @DisplayName("대기 중인 요청이 있으면 또 요청할 수 없다")
    void rejectsSecondPending() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        carpoolRequestService.send(requester.getId(), carpool.getId(), "첫 요청");

        assertThatThrownBy(() -> carpoolRequestService.send(requester.getId(), carpool.getId(), "두 번째"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CarpoolErrorCode.REQUEST_ALREADY_PENDING);
        assertThat(requestsOf(carpool)).hasSize(1);
    }

    @Test
    @DisplayName("같은 카풀에는 거절된 요청을 포함해 총 3번까지 요청한다")
    void limitsToThreeRequests() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        rejectedRequest(carpool);
        rejectedRequest(carpool);

        carpoolRequestService.send(requester.getId(), carpool.getId(), "세 번째");
        changeAllToRejected(carpool);

        assertThatThrownBy(() -> carpoolRequestService.send(requester.getId(), carpool.getId(), "네 번째"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CarpoolErrorCode.REQUEST_LIMIT_EXCEEDED);
        assertThat(requestsOf(carpool)).hasSize(3);
    }

    @Test
    @DisplayName("다른 카풀에 보낸 요청은 횟수 · 중복에 섞이지 않는다")
    void otherCarpoolsAreIndependent() {
        Companion first = persistCarpool(RECRUITING, 1);
        Companion second = persistCarpool(RECRUITING, 1);
        carpoolRequestService.send(requester.getId(), first.getId(), "요청");

        carpoolRequestService.send(requester.getId(), second.getId(), "다른 카풀도 요청");

        assertThat(requestsOf(second)).hasSize(1);
    }

    @Test
    @DisplayName("없는 id 나 택시팟 id 는 CARPOOL_NOT_FOUND다")
    void notFound() {
        Companion pot = entityManager.persistAndFlush(taxiPot(host, RECRUITING, 1));

        assertErrorCode(() -> carpoolRequestService.send(requester.getId(), pot.getId(), "요청"),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
        assertErrorCode(() -> carpoolRequestService.send(requester.getId(), pot.getId() + 1000, "요청"),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴했거나 없는 사용자는 카풀 상태와 상관없이 UNAUTHORIZED이고 요청을 남기지 않는다")
    void rejectsWithdrawnOrUnknownUser() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        requester.withdraw(NOW);
        entityManager.flush();

        assertRejected(() -> carpoolRequestService.send(requester.getId(), carpool.getId(), "요청"),
                CommonErrorCode.UNAUTHORIZED, carpool);
        assertErrorCode(() -> carpoolRequestService.send(requester.getId() + 1000, carpool.getId() + 1000, "요청"),
                CommonErrorCode.UNAUTHORIZED);
    }

    private Companion persistCarpool(CompanionStatus status, int currentCount) {
        Companion carpool = entityManager.persist(carpool(host, status, currentCount));
        entityManager.persist(participant(carpool, host, OutcomeStatus.PENDING));
        entityManager.flush();
        return carpool;
    }

    private void rejectedRequest(Companion carpool) {
        entityManager.persist(CompanionRequest.send(carpool, requester, "예전 요청"));
        entityManager.flush();
        changeAllToRejected(carpool);
    }

    private void changeAllToRejected(Companion carpool) {
        entityManager.getEntityManager()
                .createQuery("UPDATE CompanionRequest r SET r.status = :status WHERE r.companion.id = :id")
                .setParameter("status", CompanionRequestStatus.REJECTED)
                .setParameter("id", carpool.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private List<CompanionRequest> requestsOf(Companion carpool) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT r FROM CompanionRequest r WHERE r.companion.id = :id ORDER BY r.id", CompanionRequest.class)
                .setParameter("id", carpool.getId())
                .getResultList();
    }

    private void assertRejected(ThrowingCallable call, ErrorCode expected, Companion carpool) {
        assertErrorCode(call, expected);
        assertThat(requestsOf(carpool)).isEmpty();
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }
}
