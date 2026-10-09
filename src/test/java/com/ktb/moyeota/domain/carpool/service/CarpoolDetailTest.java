package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.CANCELED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetailForViewer;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequest;
import com.ktb.moyeota.domain.carpool.model.MyRequestStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.OwnedCar;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({CarpoolService.class, CarpoolDetailReader.class, NearbyCarpoolCursorCodec.class, CarpoolDetailTest.Config.class})
class CarpoolDetailTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private CarpoolService carpoolService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("로그인하지 않아도 방장 실명 · 차종 · 참여자(참여 순) · 인원을 보여 주고 내 요청은 없다")
    void anonymous() {
        User host = entityManager.persist(User.register("김방장", "방장닉", Gender.MALE, null));
        User member = entityManager.persist(User.register("이동승", "동승닉", Gender.FEMALE, "profiles/9.jpg"));
        entityManager.persist(OwnedCar.register(host, "포르쉐 911", "12가3456"));
        Companion carpool = entityManager.persist(carpool(host, RECRUITING, 2));
        entityManager.persist(participant(carpool, host, DEPARTURE_AT.minusMinutes(30)));
        entityManager.persist(participant(carpool, member, DEPARTURE_AT.minusMinutes(20)));
        flushAndClear();

        CarpoolDetailForViewer viewed = carpoolService.findDetail(carpool.getId(), null);

        CarpoolDetail detail = viewed.detail();
        assertThat(detail.id()).isEqualTo(carpool.getId());
        assertThat(detail.status()).isEqualTo(RECRUITING);
        assertThat(detail.host()).isEqualTo(new Member(host.getId(), "김방장", null));
        assertThat(detail.carModel()).isEqualTo("포르쉐 911");
        assertThat(detail.currentCount()).isEqualTo(2);
        assertThat(detail.capacity()).isEqualTo(4);
        assertThat(detail.full()).isFalse();
        assertThat(detail.participants()).extracting(Member::name).containsExactly("김방장", "이동승");
        assertThat(detail.participants().get(1).profileImageUrl()).isEqualTo("https://cdn.moyeota.test/profiles/9.jpg");
        assertThat(viewed.myRequest()).isEmpty();
    }

    @Test
    @DisplayName("로그인했어도 요청을 보낸 적이 없으면 내 요청은 없다")
    void noRequest() {
        Companion carpool = persistCarpool(RECRUITING);
        User viewer = persistUser("구경꾼");
        flushAndClear();

        assertThat(carpoolService.findDetail(carpool.getId(), viewer.getId()).myRequest()).isEmpty();
    }

    @Test
    @DisplayName("요청을 여러 번 보냈으면 가장 최근 요청을 내 요청으로 보여 준다")
    void latestRequest() {
        Companion carpool = persistCarpool(RECRUITING);
        User viewer = persistUser("요청자");
        CompanionRequest rejected = entityManager.persist(CompanionRequest.send(carpool, viewer, "첫 요청"));
        entityManager.flush();
        changeStatus(rejected, CompanionRequestStatus.REJECTED);
        CompanionRequest latest = entityManager.persist(CompanionRequest.send(carpool, viewer, "다시 요청"));
        flushAndClear();

        assertThat(carpoolService.findDetail(carpool.getId(), viewer.getId()).myRequest())
                .contains(new MyCarpoolRequest(latest.getId(), MyRequestStatus.PENDING));
    }

    @Test
    @DisplayName("모집이 끝난 카풀에 남은 대기 요청은 만료로 보여 준다")
    void expiredRequest() {
        Companion carpool = persistCarpool(IN_PROGRESS);
        User viewer = persistUser("요청자");
        CompanionRequest pending = entityManager.persist(CompanionRequest.send(carpool, viewer, "요청"));
        flushAndClear();

        assertThat(carpoolService.findDetail(carpool.getId(), viewer.getId()).myRequest())
                .contains(new MyCarpoolRequest(pending.getId(), MyRequestStatus.EXPIRED));
    }

    @Test
    @DisplayName("다른 사람의 요청은 내 요청으로 보이지 않는다")
    void othersRequestIsNotMine() {
        Companion carpool = persistCarpool(RECRUITING);
        User requester = persistUser("요청자");
        User viewer = persistUser("구경꾼");
        entityManager.persist(CompanionRequest.send(carpool, requester, "요청"));
        flushAndClear();

        assertThat(carpoolService.findDetail(carpool.getId(), viewer.getId()).myRequest()).isEmpty();
    }

    @Test
    @DisplayName("취소된 카풀도 상세는 보여 준다")
    void canceled() {
        Companion carpool = persistCarpool(CANCELED);
        flushAndClear();

        assertThat(carpoolService.findDetail(carpool.getId(), null).detail().status()).isEqualTo(CANCELED);
    }

    @Test
    @DisplayName("없는 id 나 택시팟 id 는 CARPOOL_NOT_FOUND다")
    void notFound() {
        User host = persistUser("택시방장");
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 1));
        flushAndClear();

        assertNotFound(pot.getId());
        assertNotFound(pot.getId() + 1000);
    }

    private Companion persistCarpool(CompanionStatus status) {
        User host = persistUser("방장");
        Companion carpool = entityManager.persist(carpool(host, status, 1));
        entityManager.persist(participant(carpool, host, DEPARTURE_AT.minusMinutes(30)));
        return carpool;
    }

    private User persistUser(String nickname) {
        return entityManager.persist(User.register("홍길동", nickname, Gender.MALE, null));
    }

    private void changeStatus(CompanionRequest request, CompanionRequestStatus status) {
        entityManager.getEntityManager()
                .createQuery("UPDATE CompanionRequest r SET r.status = :status WHERE r.id = :id")
                .setParameter("status", status)
                .setParameter("id", request.getId())
                .executeUpdate();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertNotFound(Long id) {
        assertThatThrownBy(() -> carpoolService.findDetail(id, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CarpoolErrorCode.CARPOOL_NOT_FOUND);
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
