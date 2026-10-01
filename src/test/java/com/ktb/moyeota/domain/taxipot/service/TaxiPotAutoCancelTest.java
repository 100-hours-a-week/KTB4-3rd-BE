package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.CANCELED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
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
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotAutoCancelTest.FixedClock.class})
class TaxiPotAutoCancelTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    private final User host = user("방장");
    private final User member = user("동승자");

    @Test
    @DisplayName("출발 12시간이 지난 모집 중 팟을 찾는다")
    void findsDuePot() {
        ChatRoom chatRoom = persistPot(RECRUITING);

        assertThat(taxiPotService.findAutoCancelDueIds()).containsExactly(chatRoom.getCompanion().getId());
    }

    @Test
    @DisplayName("자동 취소하면 팟이 취소되고 남은 참여가 끝나며 채팅방이 닫힌다")
    void cancelsPot() {
        ChatRoom chatRoom = persistPot(RECRUITING);
        Companion pot = chatRoom.getCompanion();

        taxiPotService.autoCancel(pot.getId());

        entityManager.flush();
        entityManager.clear();
        Companion canceled = entityManager.find(Companion.class, pot.getId());
        assertThat(canceled.getStatus()).isEqualTo(CANCELED);
        assertThat(canceled.getCurrentCount()).isZero();
        assertThat(entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :id", CompanionParticipant.class)
                .setParameter("id", pot.getId())
                .getResultList())
                .allSatisfy(participant -> {
                    assertThat(participant.getOutcomeStatus()).isEqualTo(INCOMPLETE);
                    assertThat(participant.getLeftAt()).isNotNull();
                });
        assertThat(entityManager.find(ChatRoom.class, chatRoom.getId()).getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("취소한 팟은 다음 배치에서 다시 찾지 않는다")
    void notFoundAgainAfterCancel() {
        ChatRoom chatRoom = persistPot(RECRUITING);

        taxiPotService.autoCancel(chatRoom.getCompanion().getId());

        assertThat(taxiPotService.findAutoCancelDueIds()).isEmpty();
    }

    @Test
    @DisplayName("찾은 뒤 운행이 시작된 팟은 건드리지 않는다")
    void skipsWhenNoLongerRecruiting() {
        ChatRoom chatRoom = persistPot(IN_PROGRESS);
        Companion pot = chatRoom.getCompanion();

        taxiPotService.autoCancel(pot.getId());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(Companion.class, pot.getId()).getStatus()).isEqualTo(IN_PROGRESS);
        assertThat(entityManager.find(ChatRoom.class, chatRoom.getId()).getClosedAt()).isNull();
    }

    private ChatRoom persistPot(CompanionStatus status) {
        entityManager.persist(host);
        entityManager.persist(member);
        Companion pot = entityManager.persist(taxiPot(host, status, 2));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, member, PENDING));
        return entityManager.persistAndFlush(ChatRoom.create(pot));
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.plusHours(12).plusMinutes(1).atZone(kst).toInstant(), kst);
        }
    }
}
