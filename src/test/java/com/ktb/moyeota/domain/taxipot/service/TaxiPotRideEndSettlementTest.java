package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotDetail;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
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
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotRideEndSettlementTest.FixedClock.class})
class TaxiPotRideEndSettlementTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("운행을 종료하면 방장 계좌와 탑승 인원을 담은 대기 상태의 정산이 생긴다")
    void opensSettlementOnRideEnd() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        Companion pot = persistTaxiPot(host, IN_PROGRESS, 3);

        taxiPotService.changeStatus(host.getId(), pot.getId(), COMPLETED);

        assertThat(settlementsOf(pot)).singleElement().satisfies(settlement -> {
            assertThat(settlement.getPayee().getId()).isEqualTo(host.getId());
            assertThat(settlement.getPayeeBankName()).isEqualTo("kb");
            assertThat(settlement.getPayeeAccountNo()).containsExactly(1);
            assertThat(settlement.getParticipantCount()).isEqualTo(3);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
            assertThat(settlement.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.PENDING);
            assertThat(settlement.getAnalysisTrialCount()).isZero();
            assertThat(settlement.getTotalAmount()).isNull();
        });
    }

    @Test
    @DisplayName("운행을 종료한 응답과 상세 조회에 정산 id가 담긴다")
    void exposesSettlementId() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        Companion pot = persistTaxiPot(host, IN_PROGRESS, 2);

        TaxiPotDetail completed = taxiPotService.changeStatus(host.getId(), pot.getId(), COMPLETED);

        Long settlementId = settlementsOf(pot).getFirst().getId();
        assertThat(completed.settlementId()).isEqualTo(settlementId);
        assertThat(taxiPotService.find(host.getId(), pot.getId()).settlementId()).isEqualTo(settlementId);
    }

    @Test
    @DisplayName("운행을 시작해도 정산은 생기지 않는다")
    void noSettlementOnRideStart() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        Companion pot = persistTaxiPot(host, RECRUITING, 2);

        TaxiPotDetail started = taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS);

        assertThat(started.settlementId()).isNull();
        assertThat(settlementsOf(pot)).isEmpty();
    }

    private Companion persistTaxiPot(User host, CompanionStatus status, int currentCount) {
        Companion pot = entityManager.persist(taxiPot(host, status, currentCount));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        for (int i = 1; i < currentCount; i++) {
            entityManager.persist(participant(pot, entityManager.persist(bankAccountHolder("동승자" + i)), PENDING));
        }
        entityManager.flush();
        return pot;
    }

    private List<Settlement> settlementsOf(Companion pot) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.getEntityManager()
                .createQuery("SELECT s FROM Settlement s JOIN FETCH s.payee WHERE s.companion.id = :potId",
                        Settlement.class)
                .setParameter("potId", pot.getId())
                .getResultList();
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
