package com.ktb.moyeota.domain.taxipot.scheduler;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({TaxiPotRideEndScheduler.class, TaxiPotService.class, ChatSystemMessageService.class,
        TestClockConfig.class})
class TaxiPotRideEndSweepTest {

    @Autowired
    private MutableClock clock;

    @Autowired
    private TaxiPotRideEndScheduler scheduler;

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void fixClock() {
        clock.set(DEPARTURE_AT.plusHours(1));
    }

    @Test
    @DisplayName("운행 종료 확인 메시지를 못 만든 팟은 다음 주기에 다시 시도한다")
    void retriesOnNextSweep() {
        Companion pot = persistRidingPotWithoutChatRoom();

        scheduler.sweep();

        assertThat(taxiPotService.findRideEndDueIds()).containsExactly(pot.getId());

        ChatRoom chatRoom = entityManager.persistAndFlush(ChatRoom.create(pot));
        scheduler.sweep();

        assertThat(messageTypes(chatRoom)).containsExactly(MessageType.SYSTEM_RIDE_END_REQUESTED);
        assertThat(taxiPotService.findRideEndDueIds()).isEmpty();
    }

    private Companion persistRidingPotWithoutChatRoom() {
        User host = entityManager.persist(user("방장"));
        Companion pot = taxiPot(host, IN_PROGRESS, 2);
        ReflectionTestUtils.setField(pot, "etaAt", DEPARTURE_AT);
        entityManager.persist(pot);
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        return pot;
    }

    private List<MessageType> messageTypes(ChatRoom chatRoom) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.id = :roomId ORDER BY m.id", Message.class)
                .setParameter("roomId", chatRoom.getId())
                .getResultList().stream()
                .map(Message::getMessageType)
                .toList();
    }
}
