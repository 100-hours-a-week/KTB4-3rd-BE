package com.ktb.moyeota.domain.carpool.scheduler;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
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
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
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
@Import({CarpoolRideStartScheduler.class, CarpoolRideEndScheduler.class, CarpoolRideService.class,
        CarpoolDetailReader.class, ChatSystemMessageService.class, CarpoolRideCardSweepTest.Config.class})
class CarpoolRideCardSweepTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.plusMinutes(1);

    @Autowired
    private CarpoolRideStartScheduler startScheduler;

    @Autowired
    private CarpoolRideEndScheduler endScheduler;

    @Autowired
    private TestEntityManager entityManager;

    private int hosts;

    @Test
    @DisplayName("출발 시각이 지난 2명 이상 모집 중 카풀에 운행 시작 카드를 한 번만 만든다")
    void startCardOnce() {
        ChatRoom room = persistRoom(persistCarpool(RECRUITING, 2));

        startScheduler.sweep();
        startScheduler.sweep();

        assertThat(messageTypes(room)).containsExactly(MessageType.SYSTEM_RIDE_START_REQUESTED);
    }

    @Test
    @DisplayName("혼자 남았거나 출발 전이거나 이미 운행 중인 카풀, 택시팟에는 시작 카드를 만들지 않는다")
    void noStartCard() {
        ChatRoom alone = persistRoom(persistCarpool(RECRUITING, 1));
        Companion early = persistCarpool(RECRUITING, 2);
        ReflectionTestUtils.setField(early, "departureAt", NOW.plusMinutes(1));
        ChatRoom notYet = persistRoom(early);
        ChatRoom riding = persistRoom(persistCarpool(IN_PROGRESS, 2));
        ChatRoom pot = persistRoom(persistTaxiPot(RECRUITING));

        startScheduler.sweep();

        assertThat(messageTypes(alone)).isEmpty();
        assertThat(messageTypes(notYet)).isEmpty();
        assertThat(messageTypes(riding)).isEmpty();
        assertThat(messageTypes(pot)).isEmpty();
    }

    @Test
    @DisplayName("채팅방이 없어 카드를 못 만든 카풀은 다음 주기에 다시 시도한다")
    void retriesStartCard() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        entityManager.flush();

        startScheduler.sweep();
        ChatRoom room = persistRoom(carpool);
        startScheduler.sweep();

        assertThat(messageTypes(room)).containsExactly(MessageType.SYSTEM_RIDE_START_REQUESTED);
    }

    @Test
    @DisplayName("도착 예정 시각이 지난 운행 중 카풀에 운행 종료 카드를 한 번만 만들고, 아직이거나 택시팟이면 만들지 않는다")
    void endCard() {
        ChatRoom arrived = persistRoom(persistRiding(NOW));
        ChatRoom onTheWay = persistRoom(persistRiding(NOW.plusMinutes(5)));
        ChatRoom recruiting = persistRoom(persistCarpool(RECRUITING, 2));
        Companion pot = persistTaxiPot(IN_PROGRESS);
        ReflectionTestUtils.setField(pot, "etaAt", NOW.minusMinutes(1));
        ChatRoom potRoom = persistRoom(pot);

        endScheduler.sweep();
        endScheduler.sweep();

        assertThat(messageTypes(arrived)).containsExactly(MessageType.SYSTEM_RIDE_END_REQUESTED);
        assertThat(messageTypes(onTheWay)).isEmpty();
        assertThat(messageTypes(recruiting)).isEmpty();
        assertThat(messageTypes(potRoom)).isEmpty();
    }

    private Companion persistCarpool(CompanionStatus status, int currentCount) {
        User host = entityManager.persist(user("방장" + ++hosts));
        Companion carpool = entityManager.persist(carpool(host, status, currentCount));
        entityManager.persist(participant(carpool, host, PENDING));
        return carpool;
    }

    private Companion persistRiding(LocalDateTime etaAt) {
        Companion carpool = persistCarpool(IN_PROGRESS, 2);
        ReflectionTestUtils.setField(carpool, "etaAt", etaAt);
        return carpool;
    }

    private Companion persistTaxiPot(CompanionStatus status) {
        User host = entityManager.persist(user("택시방장" + ++hosts));
        Companion pot = entityManager.persist(taxiPot(host, status, 2));
        entityManager.persist(participant(pot, host, PENDING));
        return pot;
    }

    private ChatRoom persistRoom(Companion companion) {
        ChatRoom room = entityManager.persist(ChatRoom.create(companion));
        entityManager.flush();
        return room;
    }

    private List<MessageType> messageTypes(ChatRoom room) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.id = :id ORDER BY m.id", Message.class)
                .setParameter("id", room.getId())
                .getResultList().stream()
                .map(Message::getMessageType)
                .toList();
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
