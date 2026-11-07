package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.batch.CongestionWindowCloseScheduler;
import com.ktb.moyeota.domain.taxipot.scheduler.TaxiPotBatchMatchingScheduler;
import com.ktb.moyeota.domain.taxipot.scheduler.TaxiPotRideStartScheduler;
import com.ktb.moyeota.global.outbox.publisher.OutboxPublishScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {"APP_ROLE=matching", "moyeota.taxi-pot.matching.mode=batch"})
class MatchingRoleTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("matching 역할은 worker 의 일(스케줄러 · 아웃박스 발행기 · RDB 판 묶음 매칭 · 혼잡도 배치)을 켜지 않는다")
    void runsNoWorkerJobs() {
        assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TaxiPotBatchMatchingScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OutboxPublishScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TaxiPotRideStartScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(CongestionWindowCloseScheduler.class)).isEmpty();
    }
}
