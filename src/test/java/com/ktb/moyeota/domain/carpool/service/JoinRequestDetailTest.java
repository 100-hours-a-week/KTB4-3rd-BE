package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
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
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import com.ktb.moyeota.domain.carpool.model.JoinRequestDetail;
import com.ktb.moyeota.domain.carpool.model.MyRequestStatus;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({MyCarpoolRequestService.class, CarpoolRequestCursorCodec.class, JoinRequestDetailTest.Config.class})
class JoinRequestDetailTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private MyCarpoolRequestService myCarpoolRequestService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User requester;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
        requester = entityManager.persist(User.register("이루디", "루디", Gender.FEMALE, "profiles/9.jpg"));
    }

    @Test
    @DisplayName("방장은 요청 내용과 요청자 실명 · 프로필을 본다")
    void hostSeesDetail() {
        Companion carpool = persistCarpool(RECRUITING);
        CompanionRequest request = persistRequest(carpool, CompanionRequestStatus.PENDING);
        flushAndClear();

        JoinRequestDetail detail = myCarpoolRequestService.findDetail(host.getId(), carpool.getId(), request.getId());

        assertThat(detail.id()).isEqualTo(request.getId());
        assertThat(detail.carpoolId()).isEqualTo(carpool.getId());
        assertThat(detail.status()).isEqualTo(MyRequestStatus.PENDING);
        assertThat(detail.content()).isEqualTo("판교역에서 같이 가고 싶습니다!");
        assertThat(detail.requester())
                .isEqualTo(new Member(requester.getId(), "이루디", "https://cdn.moyeota.test/profiles/9.jpg"));
        assertThat(detail.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("상태는 목록과 같은 규칙이다: 모집이 끝난 카풀의 대기 요청은 만료, 처리된 요청은 그대로")
    void statusFollowsListRule() {
        Companion riding = persistCarpool(IN_PROGRESS);
        CompanionRequest pending = persistRequest(riding, CompanionRequestStatus.PENDING);
        CompanionRequest accepted = persistRequest(persistCarpool(IN_PROGRESS), CompanionRequestStatus.ACCEPTED);
        CompanionRequest rejected = persistRequest(persistCarpool(RECRUITING), CompanionRequestStatus.REJECTED);
        flushAndClear();

        assertThat(statusOf(pending)).isEqualTo(MyRequestStatus.EXPIRED);
        assertThat(statusOf(accepted)).isEqualTo(MyRequestStatus.ACCEPTED);
        assertThat(statusOf(rejected)).isEqualTo(MyRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("방장이 아니면 요청자 본인이어도, 없는 요청이어도 HOST_ONLY다")
    void hostOnly() {
        Companion carpool = persistCarpool(RECRUITING);
        CompanionRequest request = persistRequest(carpool, CompanionRequestStatus.PENDING);
        flushAndClear();

        assertErrorCode(() -> myCarpoolRequestService.findDetail(requester.getId(), carpool.getId(), request.getId()),
                CarpoolErrorCode.HOST_ONLY);
        assertErrorCode(() -> myCarpoolRequestService.findDetail(requester.getId(), carpool.getId(), request.getId() + 1000),
                CarpoolErrorCode.HOST_ONLY);
    }

    @Test
    @DisplayName("없는 카풀 · 택시팟 id 는 CARPOOL_NOT_FOUND, 없는 요청 · 다른 카풀의 요청은 CARPOOL_REQUEST_NOT_FOUND다")
    void notFound() {
        Companion carpool = persistCarpool(RECRUITING);
        CompanionRequest otherCarpoolRequest = persistRequest(persistCarpool(RECRUITING), CompanionRequestStatus.PENDING);
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 1));
        flushAndClear();

        assertErrorCode(() -> myCarpoolRequestService.findDetail(host.getId(), pot.getId() + 1000, otherCarpoolRequest.getId()),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
        assertErrorCode(() -> myCarpoolRequestService.findDetail(host.getId(), pot.getId(), otherCarpoolRequest.getId()),
                CarpoolErrorCode.CARPOOL_NOT_FOUND);
        assertErrorCode(() -> myCarpoolRequestService.findDetail(host.getId(), carpool.getId(), otherCarpoolRequest.getId()),
                CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND);
        assertErrorCode(() -> myCarpoolRequestService.findDetail(host.getId(), carpool.getId(), otherCarpoolRequest.getId() + 1000),
                CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴한 방장은 UNAUTHORIZED다")
    void withdrawnHost() {
        Companion carpool = persistCarpool(RECRUITING);
        CompanionRequest request = persistRequest(carpool, CompanionRequestStatus.PENDING);
        host.withdraw(NOW);
        flushAndClear();

        assertErrorCode(() -> myCarpoolRequestService.findDetail(host.getId(), carpool.getId(), request.getId()),
                CommonErrorCode.UNAUTHORIZED);
    }

    private MyRequestStatus statusOf(CompanionRequest request) {
        Long carpoolId = entityManager.find(CompanionRequest.class, request.getId()).getCompanion().getId();
        return myCarpoolRequestService.findDetail(host.getId(), carpoolId, request.getId()).status();
    }

    private Companion persistCarpool(CompanionStatus status) {
        Companion carpool = entityManager.persist(carpool(host, status, status == RECRUITING ? 1 : 2));
        entityManager.persist(participant(carpool, host, OutcomeStatus.PENDING));
        return carpool;
    }

    private CompanionRequest persistRequest(Companion carpool, CompanionRequestStatus status) {
        CompanionRequest request = CompanionRequest.send(carpool, requester, "판교역에서 같이 가고 싶습니다!");
        ReflectionTestUtils.setField(request, "status", status);
        return entityManager.persist(request);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
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

        @Bean
        ImageUrlResolver imageUrlResolver() {
            return new ImageUrlResolver(new S3Properties(
                    "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test"));
        }
    }
}
