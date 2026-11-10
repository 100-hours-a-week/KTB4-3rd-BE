package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({TaxiPotService.class, ChatSystemMessageService.class, MatchingOutboxListener.class,
        TestClockConfig.class})
@TestPropertySource(properties = "moyeota.taxi-pot.matching.mode=batch")
class MatchingOutboxBatchModeTest {

    @Autowired
    private MutableClock clock;

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void fixClock() {
        clock.set(DEPARTURE_AT.minusHours(1));
    }

    @Test
    @DisplayName("묶음 매칭(batch) 모드에서는 매칭 요청을 아웃박스에 기록하지 않는다")
    void doesNotRecordInBatchMode() {
        User me = entityManager.persist(bankAccountHolder("요청자"));

        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.cancelMatching(me.getId());

        entityManager.flush();
        assertThat(outboxEventRepository.findAll()).isEmpty();
    }
}
