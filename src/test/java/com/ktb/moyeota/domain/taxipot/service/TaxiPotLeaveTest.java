package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
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
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotLeaveTest.FixedClock.class})
class TaxiPotLeaveTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("동승자가 나가면 인원이 줄고 나간 동승자는 참여 중인 택시팟 매칭이 없다")
    void memberLeaves() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(member.getId(), pot.id());

        assertThat(reload(pot).getCurrentCount()).isEqualTo(1);
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("방장이 나가면 가장 먼저 들어왔던 사람이 방장이 된다")
    void hostLeaves() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User first = entityManager.persist(bankAccountHolder("먼저온사람"));
        User second = entityManager.persist(bankAccountHolder("나중온사람"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(first.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(second.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(host.getId(), pot.id());

        assertThat(reload(pot).getHost().getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("마지막 한 명이 나가면 취소된다")
    void lastOneLeaves() {
        User host = entityManager.persist(bankAccountHolder("혼자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(host.getId(), pot.id());

        assertThat(reload(pot).getStatus()).isEqualTo(CompanionStatus.CANCELED);
    }

    @Test
    @DisplayName("마지막 한 명이 나가 취소되면 채팅방이 닫힌다")
    void closesChatRoomWhenCanceled() {
        User host = entityManager.persist(bankAccountHolder("혼자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(host.getId(), pot.id());

        assertThat(reloadChatRoom(pot).getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("남은 사람이 있으면 채팅방은 닫히지 않는다")
    void keepsChatRoomOpenWhileOthersRemain() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(member.getId(), pot.id());

        assertThat(reloadChatRoom(pot).getClosedAt()).isNull();
    }

    @Test
    @DisplayName("정원이 찬 팟에서 한 명이 나가면 다시 다른 사람이 합류할 수 있다")
    void seatOpensAgain() {
        CurrentTaxiPot full = null;
        User leaving = null;
        for (int i = 1; i <= 4; i++) {
            leaving = entityManager.persist(bankAccountHolder("탑승자" + i));
            full = taxiPotService.start(leaving.getId(), startCommand(DEPARTURE_AT));
        }
        assertThat(full.currentCount()).isEqualTo(full.capacity());
        taxiPotService.leave(leaving.getId(), full.id());
        User newcomer = entityManager.persist(bankAccountHolder("새로온사람"));

        CurrentTaxiPot joined = taxiPotService.start(newcomer.getId(), startCommand(DEPARTURE_AT));

        assertThat(joined.id()).isEqualTo(full.id());
        assertThat(joined.currentCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("나갔다가 같은 조건으로 다시 매칭하면 같은 팟에 예전 참여를 되살려 합류한다")
    void leaveThenRejoin() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.leave(member.getId(), pot.id());

        CurrentTaxiPot rejoined = taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));

        assertThat(rejoined.id()).isEqualTo(pot.id());
        assertThat(rejoined.currentCount()).isEqualTo(2);
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(member.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 나간 사람이 다시 나가면 TAXI_POT_NOT_FOUND다")
    void leaveTwice() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.leave(member.getId(), pot.id());

        assertThatThrownBy(() -> taxiPotService.leave(member.getId(), pot.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
    }

    @Test
    @DisplayName("나가면 채팅방에 나간 사람의 퇴장 메시지가 남는다")
    void leavesLeaveMessage() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(member.getId(), pot.id());

        assertThat(leaverIds(pot.id())).containsExactly(member.getId());
    }

    @Test
    @DisplayName("완주한 사람이 나가면 퇴장 메시지가 남지 않는다")
    void noLeaveMessageWhenCompleted() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, OutcomeStatus.PENDING));
        entityManager.persistAndFlush(participant(pot, member, OutcomeStatus.COMPLETED));

        taxiPotService.leave(member.getId(), pot.getId());

        assertThat(leaverIds(pot.getId())).isEmpty();
    }

    @Test
    @DisplayName("운행 중이라 나가기가 거절되면 퇴장 메시지가 남지 않는다")
    void noLeaveMessageWhenRejected() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.IN_PROGRESS, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, OutcomeStatus.PENDING));
        entityManager.persistAndFlush(participant(pot, member, OutcomeStatus.PENDING));

        assertThatThrownBy(() -> taxiPotService.leave(member.getId(), pot.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(leaverIds(pot.getId())).isEmpty();
    }

    private List<Long> leaverIds(Long taxiPotId) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.companion.id = :potId"
                        + " AND m.messageType = :type ORDER BY m.id", Message.class)
                .setParameter("potId", taxiPotId)
                .setParameter("type", MessageType.SYSTEM_LEAVE)
                .getResultList().stream()
                .map(m -> m.getSender().getId())
                .toList();
    }

    private ChatRoom reloadChatRoom(CurrentTaxiPot pot) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(ChatRoom.class, pot.chatRoomId());
    }

    private Companion reload(CurrentTaxiPot pot) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, pot.id());
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
