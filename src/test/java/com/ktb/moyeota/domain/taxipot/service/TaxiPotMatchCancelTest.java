package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
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
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotMatchCancelTest.FixedClock.class})
@TestPropertySource(properties = "moyeota.taxi-pot.matching.mode=batch")
class TaxiPotMatchCancelTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("찾는 중 요청을 취소하면 취소됨이 되고 바로 새 매칭을 요청할 수 있다")
    void cancelsMatchingRequest() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.cancelMatching(me.getId());

        assertThat(requestsOf(me)).extracting(TaxiPotMatchRequest::getStatus)
                .containsExactly(TaxiPotMatchRequestStatus.CANCELED);
        assertThat(taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT)).isMatching()).isTrue();
        assertThat(requestsOf(me)).extracting(TaxiPotMatchRequest::getStatus)
                .containsExactly(TaxiPotMatchRequestStatus.CANCELED, TaxiPotMatchRequestStatus.MATCHING);
    }

    @Test
    @DisplayName("다른 사람의 찾는 중 요청은 건드리지 않는다")
    void keepsOthersRequest() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        User other = entityManager.persist(bankAccountHolder("다른요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(other.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.cancelMatching(me.getId());

        assertThat(requestsOf(other)).extracting(TaxiPotMatchRequest::getStatus)
                .containsExactly(TaxiPotMatchRequestStatus.MATCHING);
    }

    @Test
    @DisplayName("찾는 중 요청이 없으면 404 MATCH_REQUEST_NOT_FOUND다")
    void rejectsWithoutMatchingRequest() {
        User me = entityManager.persist(bankAccountHolder("요청자"));

        assertErrorCode(me, TaxiPotErrorCode.MATCH_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 취소했으면 두 번째 취소는 404 MATCH_REQUEST_NOT_FOUND다")
    void rejectsSecondCancel() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.cancelMatching(me.getId());

        assertErrorCode(me, TaxiPotErrorCode.MATCH_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("출발 시각이 지나 실패로 끝난 요청만 있으면 404 MATCH_REQUEST_NOT_FOUND다")
    void rejectsWhenOnlyFailedRequest() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        changeStatus(requestsOf(me).getFirst(), TaxiPotMatchRequestStatus.FAILED);

        assertErrorCode(me, TaxiPotErrorCode.MATCH_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 팟에 묶였으면 409 MATCH_ALREADY_COMPLETED이고 요청은 매칭됨 그대로다")
    void rejectsWhenAlreadyMatched() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.RECRUITING, 2));
        entityManager.persist(participant(pot, me, OutcomeStatus.PENDING));
        requestsOf(me).getFirst().matchTo(pot);
        entityManager.flush();

        assertErrorCode(me, TaxiPotErrorCode.MATCH_ALREADY_COMPLETED);
        assertThat(requestsOf(me)).extracting(TaxiPotMatchRequest::getStatus)
                .containsExactly(TaxiPotMatchRequestStatus.MATCHED);
    }

    private void assertErrorCode(User user, TaxiPotErrorCode expected) {
        assertThatThrownBy(() -> taxiPotService.cancelMatching(user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private List<TaxiPotMatchRequest> requestsOf(User user) {
        entityManager.flush();
        entityManager.clear();
        return taxiPotMatchRequestRepository.findAll().stream()
                .filter(request -> request.getUser().getId().equals(user.getId()))
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .toList();
    }

    private void changeStatus(TaxiPotMatchRequest request, TaxiPotMatchRequestStatus status) {
        ReflectionTestUtils.setField(request, "status", status);
        entityManager.merge(request);
        entityManager.flush();
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
