package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.matching.RegionGrid;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
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
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotBatchStartTest.FixedClock.class})
@TestPropertySource(properties = "moyeota.taxi-pot.matching.mode=batch")
class TaxiPotBatchStartTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("묶음 매칭 모드에서는 팟을 만들지 않고 찾는 중으로 응답한다")
    void respondsMatching() {
        User me = entityManager.persist(bankAccountHolder("요청자"));

        CurrentTaxiPot started = taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        assertThat(started.isMatching()).isTrue();
        assertThat(started.id()).isNull();
        assertThat(started.chatRoomId()).isNull();
        assertThat(started.currentCount()).isNull();
        assertThat(started.capacity()).isEqualTo(4);
        assertThat(companions()).isEmpty();
    }

    @Test
    @DisplayName("요청은 찾는 중 상태로 분 단위 출발 시각과 출발지 2km 칸과 함께 저장된다")
    void savesMatchingRequest() {
        User me = entityManager.persist(bankAccountHolder("요청자"));

        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT.plusSeconds(40)));

        entityManager.flush();
        entityManager.clear();
        List<TaxiPotMatchRequest> saved = taxiPotMatchRequestRepository.findAll();
        RegionCell expected = new RegionGrid(2_000).cellOf(GeoPoint.of(ORIGIN_LAT, ORIGIN_LNG));
        assertThat(saved).singleElement().satisfies(request -> {
            assertThat(request.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
            assertThat(request.getUser().getId()).isEqualTo(me.getId());
            assertThat(request.getDepartureAt()).isEqualTo(DEPARTURE_AT);
            assertThat(request.getRegionRow()).isEqualTo(expected.row());
            assertThat(request.getRegionCol()).isEqualTo(expected.col());
            assertThat(request.getCompanion()).isNull();
        });
    }

    @Test
    @DisplayName("찾는 중인 동안 진행 중인 택시팟 조회는 찾는 중으로 나온다")
    void findsMatchingAsCurrent() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        assertThat(taxiPotService.findMyCurrent(me.getId()))
                .hasValueSatisfying(current -> assertThat(current.isMatching()).isTrue());
    }

    @Test
    @DisplayName("찾는 중인 요청이 있으면 다시 요청할 수 없다")
    void rejectsSecondRequest() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        assertErrorCode(me, TaxiPotErrorCode.MATCH_ALREADY_IN_PROGRESS);
        assertThat(taxiPotMatchRequestRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 참여 중인 택시팟이 있으면 찾는 중 요청을 만들지 않는다")
    void rejectsWhileInTaxiPot() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User me = entityManager.persist(bankAccountHolder("참여자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.RECRUITING, 2));
        entityManager.persistAndFlush(participant(pot, me, OutcomeStatus.PENDING));

        assertErrorCode(me, TaxiPotErrorCode.MATCH_ALREADY_IN_PROGRESS);
        assertThat(taxiPotMatchRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("정산 계좌가 없으면 찾는 중 요청을 만들지 않는다")
    void rejectsWithoutBankAccount() {
        User me = entityManager.persist(user("계좌없음"));

        assertErrorCode(me, TaxiPotErrorCode.BANK_ACCOUNT_REQUIRED);
        assertThat(taxiPotMatchRequestRepository.count()).isZero();
    }

    private void assertErrorCode(User user, TaxiPotErrorCode expected) {
        assertThatThrownBy(() -> taxiPotService.start(user.getId(), startCommand(DEPARTURE_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private List<Companion> companions() {
        return entityManager.getEntityManager()
                .createQuery("SELECT c FROM Companion c", Companion.class)
                .getResultList();
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
