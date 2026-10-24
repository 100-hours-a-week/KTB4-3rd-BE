package com.ktb.moyeota.domain.taxipot.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.taxipot.service.TaxiPotBatchMatchingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TaxiPotBatchMatchingSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TaxiPotBatchMatchingService.class, () -> mock(TaxiPotBatchMatchingService.class))
            .withUserConfiguration(TaxiPotBatchMatchingScheduler.class);

    @Test
    @DisplayName("주기마다 묶음 매칭을 한 번 실행한다")
    void runsOnce() {
        TaxiPotBatchMatchingService service = mock(TaxiPotBatchMatchingService.class);

        new TaxiPotBatchMatchingScheduler(service).sweep();

        verify(service).runOnce();
    }

    @Test
    @DisplayName("묶음 매칭이 실패해도 예외를 밖으로 던지지 않아 다음 주기가 계속 돈다")
    void swallowsFailure() {
        TaxiPotBatchMatchingService service = mock(TaxiPotBatchMatchingService.class);
        given(service.runOnce()).willThrow(new IllegalStateException("DB 연결 실패"));

        assertThatCode(() -> new TaxiPotBatchMatchingScheduler(service).sweep()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("batch 모드일 때만 스케줄러가 등록된다")
    void registeredOnlyInBatchMode() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=batch")
                .run(context -> assertThat(context).hasSingleBean(TaxiPotBatchMatchingScheduler.class));
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=instant")
                .run(context -> assertThat(context).doesNotHaveBean(TaxiPotBatchMatchingScheduler.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(TaxiPotBatchMatchingScheduler.class));
    }
}
