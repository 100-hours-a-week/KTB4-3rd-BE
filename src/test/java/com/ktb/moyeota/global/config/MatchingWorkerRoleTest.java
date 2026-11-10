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

@SpringBootTest(properties = {"APP_ROLE=matching,worker", "moyeota.taxi-pot.matching.mode=batch"})
class MatchingWorkerRoleTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("matching 과 worker 를 함께 켜면 worker 의 일을 모두 켠 채로 기동한다")
    void runsBothRoles() {
        assertThat(context.getBeanNamesForType(SchedulingConfig.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(TaxiPotBatchMatchingScheduler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(OutboxPublishScheduler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(TaxiPotRideStartScheduler.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(CongestionWindowCloseScheduler.class)).hasSize(1);
    }
}
