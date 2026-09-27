package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
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

@DataJpaTest
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotStatusChangeTest.FixedClock.class})
class TaxiPotStatusChangeTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("운행을 시작하면 응답에 바뀐 상태가 담긴다")
    void responseShowsNewStatus() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));

        assertThat(taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS).status())
                .isEqualTo(IN_PROGRESS);
    }

    @Test
    @DisplayName("운행을 시작하면 수정 시각이 갱신된다")
    void touchesUpdatedAt() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        LocalDateTime createdUpdatedAt = pot.getUpdatedAt();

        taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Companion.class, pot.getId()).getUpdatedAt()).isAfter(createdUpdatedAt);
    }

    @Test
    @DisplayName("운행을 시작하면 도착 예정 시각이 시작 시각의 1시간 뒤로 저장된다")
    void savesDefaultEta() {
        Companion pot = persistTaxiPot(RECRUITING);

        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), IN_PROGRESS);

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    @Test
    @DisplayName("운행 중인 팟은 받은 도착 예정 시각으로 바뀐다")
    void estimatesArrivalWhileRiding() {
        Companion pot = persistTaxiPot(IN_PROGRESS);

        taxiPotService.estimateArrival(pot.getId(), DEPARTURE_AT.plusMinutes(20));

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusMinutes(20));
    }

    @Test
    @DisplayName("운행이 끝난 팟은 도착 예정 시각이 바뀌지 않는다")
    void keepsEtaAfterRide() {
        Companion pot = persistTaxiPot(COMPLETED);

        taxiPotService.estimateArrival(pot.getId(), DEPARTURE_AT.plusMinutes(20));

        assertThat(reload(pot).getEtaAt()).isNull();
    }

    @Test
    @DisplayName("운행을 시작하면 채팅방에 운행 시작 알림이 남는다")
    void leavesRideStartedMessage() {
        Companion pot = persistTaxiPot(RECRUITING);

        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), IN_PROGRESS);

        assertThat(messageTypes(pot)).containsExactly(MessageType.SYSTEM_RIDE_STARTED);
    }

    @Test
    @DisplayName("운행을 종료하면 채팅방에 운행 종료 알림이 남는다")
    void leavesRideEndedMessage() {
        Companion pot = persistTaxiPot(IN_PROGRESS);

        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), COMPLETED);

        assertThat(messageTypes(pot)).containsExactly(MessageType.SYSTEM_RIDE_ENDED);
    }

    @Test
    @DisplayName("운행 시작이 거절되면 알림이 남지 않는다")
    void noMessageWhenRejected() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 1));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));

        assertThatThrownBy(() -> taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS))
                .isInstanceOf(BusinessException.class);
        assertThat(messageTypes(pot)).isEmpty();
    }

    @Test
    @DisplayName("운행을 종료하면 함께 탄 참여자가 모두 완주하고 진행 중인 택시팟이 없어진다")
    void completesRidersOnRideEnd() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = persistRidingPot(host, member);

        taxiPotService.changeStatus(host.getId(), pot.getId(), COMPLETED);

        entityManager.flush();
        assertThat(participantsOf(pot))
                .allSatisfy(p -> {
                    assertThat(p.isCompleted()).isTrue();
                    assertThat(p.getLeftAt()).isNotNull();
                });
        assertThat(taxiPotService.findMyCurrent(member.getId())).isEmpty();
    }

    private Companion persistRidingPot(User host, User member) {
        Companion pot = entityManager.persist(taxiPot(host, IN_PROGRESS, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persistAndFlush(participant(pot, member, PENDING));
        return pot;
    }

    private List<CompanionParticipant> participantsOf(Companion pot) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :potId",
                        CompanionParticipant.class)
                .setParameter("potId", pot.getId())
                .getResultList();
    }

    private List<MessageType> messageTypes(Companion pot) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.companion.id = :potId ORDER BY m.id",
                        Message.class)
                .setParameter("potId", pot.getId())
                .getResultList().stream()
                .map(Message::getMessageType)
                .toList();
    }

    private Companion persistTaxiPot(CompanionStatus status) {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, status, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        return pot;
    }

    private Companion reload(Companion pot) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, pot.getId());
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.atZone(kst).toInstant(), kst);
        }
    }
}
