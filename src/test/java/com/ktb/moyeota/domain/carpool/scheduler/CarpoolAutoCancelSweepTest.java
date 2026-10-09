package com.ktb.moyeota.domain.carpool.scheduler;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.CANCELED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.carpool.service.CarpoolDetailReader;
import com.ktb.moyeota.domain.carpool.service.CarpoolRideService;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
@Import({CarpoolAutoCancelScheduler.class, CarpoolRideService.class, CarpoolDetailReader.class,
        ChatSystemMessageService.class, CarpoolAutoCancelSweepTest.Config.class})
class CarpoolAutoCancelSweepTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.plusHours(12).plusMinutes(1);

    @Autowired
    private CarpoolAutoCancelScheduler scheduler;

    @Autowired
    private CarpoolRideService carpoolRideService;

    @Autowired
    private TestEntityManager entityManager;

    private int users;

    @Test
    @DisplayName("출발 12시간이 지난 모집 중 카풀을 취소하고, 남은 참여자를 모두 내보내고 채팅방을 닫는다")
    void cancels() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        ChatRoom room = entityManager.persist(ChatRoom.create(carpool));
        entityManager.flush();

        scheduler.sweep();

        flushAndClear();
        Companion canceled = entityManager.find(Companion.class, carpool.getId());
        assertThat(canceled.getStatus()).isEqualTo(CANCELED);
        assertThat(canceled.getCurrentCount()).isZero();
        assertThat(participantsOf(carpool)).hasSize(2).allSatisfy(participant -> {
            assertThat(participant.getOutcomeStatus()).isEqualTo(INCOMPLETE);
            assertThat(participant.getLeftAt()).isNotNull();
        });
        assertThat(entityManager.find(ChatRoom.class, room.getId()).getClosedAt()).isNotNull();
        assertThat(carpoolRideService.findAutoCancelDueIds()).isEmpty();
    }

    @Test
    @DisplayName("출발 뒤 정확히 12시간까지는 취소하지 않는다")
    void boundary() {
        Companion exactly = persistCarpool(RECRUITING, 1);
        ReflectionTestUtils.setField(exactly, "departureAt", NOW.minusHours(12));
        Companion justAfter = persistCarpool(RECRUITING, 1);
        ReflectionTestUtils.setField(justAfter, "departureAt", NOW.minusHours(12).minusSeconds(1));
        entityManager.flush();

        assertThat(carpoolRideService.findAutoCancelDueIds()).containsExactly(justAfter.getId());
    }

    @Test
    @DisplayName("운행 중인 카풀과 택시팟은 건드리지 않는다")
    void skipsOthers() {
        Companion riding = persistCarpool(IN_PROGRESS, 2);
        entityManager.persist(ChatRoom.create(riding));
        User potHost = entityManager.persist(user("택시방장"));
        Companion pot = entityManager.persist(taxiPot(potHost, RECRUITING, 1));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.flush();

        scheduler.sweep();

        flushAndClear();
        assertThat(entityManager.find(Companion.class, riding.getId()).getStatus()).isEqualTo(IN_PROGRESS);
        assertThat(entityManager.find(Companion.class, pot.getId()).getStatus()).isEqualTo(RECRUITING);
    }

    @Test
    @DisplayName("찾은 뒤 운행이 시작된 카풀은 취소하지 않는다")
    void skipsWhenNoLongerRecruiting() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        entityManager.persist(ChatRoom.create(carpool));
        entityManager.flush();
        List<Long> due = carpoolRideService.findAutoCancelDueIds();
        ReflectionTestUtils.setField(carpool, "status", IN_PROGRESS);
        entityManager.flush();

        due.forEach(carpoolRideService::autoCancel);

        flushAndClear();
        assertThat(due).containsExactly(carpool.getId());
        assertThat(entityManager.find(Companion.class, carpool.getId()).getStatus()).isEqualTo(IN_PROGRESS);
        assertThat(participantsOf(carpool)).allSatisfy(participant ->
                assertThat(participant.getOutcomeStatus()).isEqualTo(PENDING));
    }

    private Companion persistCarpool(CompanionStatus status, int currentCount) {
        User host = entityManager.persist(user("방장" + ++users));
        Companion carpool = entityManager.persist(carpool(host, status, currentCount));
        entityManager.persist(participant(carpool, host, PENDING));
        if (currentCount > 1) {
            entityManager.persist(participant(carpool, entityManager.persist(user("동승자" + ++users)), PENDING));
        }
        return carpool;
    }

    private List<CompanionParticipant> participantsOf(Companion carpool) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :id", CompanionParticipant.class)
                .setParameter("id", carpool.getId())
                .getResultList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
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
