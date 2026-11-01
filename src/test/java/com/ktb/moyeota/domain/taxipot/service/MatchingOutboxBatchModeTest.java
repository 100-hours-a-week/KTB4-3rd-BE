package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
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
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({TaxiPotService.class, ChatSystemMessageService.class, MatchingOutboxListener.class,
        MatchingOutboxBatchModeTest.FixedClock.class})
@TestPropertySource(properties = "moyeota.taxi-pot.matching.mode=batch")
class MatchingOutboxBatchModeTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("묶음 매칭(batch) 모드에서는 매칭 요청을 아웃박스에 기록하지 않는다")
    void doesNotRecordInBatchMode() {
        User me = entityManager.persist(bankAccountHolder("요청자"));

        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.cancelMatching(me.getId());

        entityManager.flush();
        assertThat(outboxEventRepository.findAll()).isEmpty();
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.minusHours(1).atZone(kst).toInstant(), kst);
        }
    }
}
