package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_NAME;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_NAME;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.CarpoolCreateCommand;
import com.ktb.moyeota.domain.carpool.model.CreatedCarpool;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.entity.TransportType;
import com.ktb.moyeota.domain.user.entity.OwnedCar;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

@DataJpaTest
@Import({CarpoolRegistrationService.class, CarpoolRegistrationServiceTest.Config.class})
class CarpoolRegistrationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 9, 0);

    @Autowired
    private CarpoolRegistrationService carpoolRegistrationService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
        entityManager.persistAndFlush(OwnedCar.register(host, "아반떼", "12가3456"));
    }

    @Test
    @DisplayName("등록하면 방장 혼자 참여한 모집 중 카풀과 열린 채팅방이 생기고, 출발 시각은 분 단위로 자른다")
    void creates() {
        CreatedCarpool created = carpoolRegistrationService.create(
                host.getId(), command(LocalDateTime.of(2026, 10, 10, 8, 30, 45), 3));

        entityManager.flush();
        entityManager.clear();
        Companion carpool = entityManager.find(Companion.class, created.id());
        assertThat(carpool.getKind()).isEqualTo(CompanionKind.CARPOOL);
        assertThat(carpool.getTransportType()).isEqualTo(TransportType.OWNED_CAR);
        assertThat(carpool.getStatus()).isEqualTo(CompanionStatus.RECRUITING);
        assertThat(carpool.getCapacity()).isEqualTo(4);
        assertThat(carpool.getCurrentCount()).isEqualTo(1);
        assertThat(carpool.getHost().getId()).isEqualTo(host.getId());
        assertThat(carpool.getDepartureAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 8, 30));
        assertThat(participantsOf(carpool)).singleElement().satisfies(participant -> {
            assertThat(participant.getUser().getId()).isEqualTo(host.getId());
            assertThat(participant.getOutcomeStatus()).isEqualTo(OutcomeStatus.PENDING);
        });
        ChatRoom chatRoom = chatRoomOf(carpool);
        assertThat(chatRoom.getClosedAt()).isNull();
        assertThat(created).isEqualTo(new CreatedCarpool(
                carpool.getId(), chatRoom.getId(), 4, 1, CompanionStatus.RECRUITING));
    }

    @Test
    @DisplayName("지금과 같은 분은 등록하고, 1분 전이면 DEPARTURE_TIME_PASSED이고 아무것도 만들지 않는다")
    void departureBoundaryInPast() {
        carpoolRegistrationService.create(host.getId(), command(NOW.withSecond(30), 1));

        assertErrorCode(() -> carpoolRegistrationService.create(host.getId(), command(NOW.minusMinutes(1), 1)),
                CarpoolErrorCode.DEPARTURE_TIME_PASSED);
        assertThat(carpoolCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 달 뒤 날짜는 그날 끝까지 등록하고, 다음 날부터는 DEPARTURE_TIME_TOO_FAR다")
    void departureBoundaryInFuture() {
        carpoolRegistrationService.create(host.getId(), command(LocalDateTime.of(2026, 11, 8, 23, 59), 1));

        assertErrorCode(() -> carpoolRegistrationService.create(
                        host.getId(), command(LocalDateTime.of(2026, 11, 9, 0, 0), 1)),
                CarpoolErrorCode.DEPARTURE_TIME_TOO_FAR);
        assertThat(carpoolCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("출발지와 도착지 좌표가 같으면 SAME_ORIGIN_DEST이고 아무것도 만들지 않는다")
    void sameCoordinates() {
        CarpoolCreateCommand sameSpot = new CarpoolCreateCommand(
                ORIGIN_NAME, ORIGIN_LAT, ORIGIN_LNG, "다른 이름", new BigDecimal("37.394500"),
                new BigDecimal("127.111200"), NOW.plusDays(1), 1);

        assertErrorCode(() -> carpoolRegistrationService.create(host.getId(), sameSpot),
                CarpoolErrorCode.SAME_ORIGIN_DEST);
        assertThat(carpoolCount()).isZero();
    }

    @Test
    @DisplayName("차량이 없으면 CAR_REGISTRATION_REQUIRED이고 아무것도 만들지 않는다")
    void requiresCar() {
        User noCar = entityManager.persistAndFlush(user("차없음"));

        assertErrorCode(() -> carpoolRegistrationService.create(noCar.getId(), command(NOW.plusDays(1), 1)),
                CarpoolErrorCode.CAR_REGISTRATION_REQUIRED);
        assertThat(carpoolCount()).isZero();
    }

    @Test
    @DisplayName("이미 방장인 카풀이 있어도 또 등록한다")
    void allowsAnotherCarpool() {
        carpoolRegistrationService.create(host.getId(), command(NOW.plusDays(1), 1));
        carpoolRegistrationService.create(host.getId(), command(NOW.plusDays(1), 2));

        assertThat(carpoolCount()).isEqualTo(2);
    }

    private static CarpoolCreateCommand command(LocalDateTime departureAt, int recruitCount) {
        return new CarpoolCreateCommand(
                ORIGIN_NAME, ORIGIN_LAT, ORIGIN_LNG, DEST_NAME, DEST_LAT, DEST_LNG, departureAt, recruitCount);
    }

    private long carpoolCount() {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT COUNT(c) FROM Companion c WHERE c.kind = :kind", Long.class)
                .setParameter("kind", CompanionKind.CARPOOL)
                .getSingleResult();
    }

    private List<CompanionParticipant> participantsOf(Companion carpool) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :id", CompanionParticipant.class)
                .setParameter("id", carpool.getId())
                .getResultList();
    }

    private ChatRoom chatRoomOf(Companion carpool) {
        return entityManager.getEntityManager()
                .createQuery("SELECT r FROM ChatRoom r WHERE r.companion.id = :id", ChatRoom.class)
                .setParameter("id", carpool.getId())
                .getSingleResult();
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
