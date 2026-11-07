package com.ktb.moyeota.domain.taxipot.scheduler;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.CANCELED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TaxiPotAutoCancelScheduler.class, TaxiPotService.class, ChatSystemMessageService.class,
        TestClockConfig.class})
class TaxiPotAutoCancelSweepTest {

    @Autowired
    private MutableClock clock;

    @Autowired
    private TaxiPotAutoCancelScheduler scheduler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaxiPotService taxiPotService;

    @MockitoSpyBean
    private TaxiPotRepository taxiPotRepository;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @AfterEach
    void tearDown() {
        chatRoomRepository.deleteAll();
        taxiPotParticipantRepository.deleteAll();
        taxiPotRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void fixClock() {
        clock.set(DEPARTURE_AT.plusHours(12).plusMinutes(1));
    }

    @Test
    @DisplayName("출발 12시간이 지난 모집 중 팟을 모두 취소한다")
    void cancelsEveryDuePot() {
        Companion first = saveDuePot("첫방장");
        Companion second = saveDuePot("둘째방장");

        scheduler.sweep();

        assertThat(taxiPotRepository.findAllById(List.of(first.getId(), second.getId())))
                .extracting(Companion::getStatus)
                .containsOnly(CANCELED);
    }

    @Test
    @DisplayName("한 팟이 실패해도 나머지는 취소하고, 실패한 팟은 다음 배치 대상으로 남는다")
    void keepsFailedPotForNextSweep() {
        Companion failing = saveDuePot("첫방장");
        Companion other = saveDuePot("둘째방장");
        willThrow(new CannotAcquireLockException("lock wait timeout"))
                .given(taxiPotRepository).findRecruitingTaxiPotForUpdate(failing.getId());

        scheduler.sweep();

        assertThat(statusOf(other)).isEqualTo(CANCELED);
        assertThat(taxiPotService.findAutoCancelDueIds()).containsExactly(failing.getId());
    }

    private Companion saveDuePot(String hostName) {
        User host = userRepository.save(user(hostName));
        Companion pot = taxiPotRepository.save(taxiPot(host, RECRUITING, 1));
        taxiPotParticipantRepository.save(participant(pot, host, PENDING));
        chatRoomRepository.save(ChatRoom.create(pot));
        return pot;
    }

    private CompanionStatus statusOf(Companion pot) {
        return taxiPotRepository.findById(pot.getId()).orElseThrow().getStatus();
    }
}
