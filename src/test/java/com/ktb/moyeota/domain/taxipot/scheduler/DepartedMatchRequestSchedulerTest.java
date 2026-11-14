package com.ktb.moyeota.domain.taxipot.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.taxipot.service.DepartedMatchRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DepartedMatchRequestSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DepartedMatchRequestService.class, () -> mock(DepartedMatchRequestService.class))
            .withUserConfiguration(DepartedMatchRequestScheduler.class);

    @Test
    @DisplayName("kafka 모드일 때만 등록된다. batch 모드는 묶음 매칭 주기가 같은 일을 한다")
    void registeredOnlyInKafkaMode() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=kafka")
                .run(context -> assertThat(context).hasSingleBean(DepartedMatchRequestScheduler.class));
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=batch")
                .run(context -> assertThat(context).doesNotHaveBean(DepartedMatchRequestScheduler.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(DepartedMatchRequestScheduler.class));
    }

    @Test
    @DisplayName("worker 역할에서만 등록된다. 스케줄링이 worker 에서만 켜진다")
    void registeredOnlyOnWorker() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=kafka", "APP_ROLE=worker")
                .run(context -> assertThat(context).hasSingleBean(DepartedMatchRequestScheduler.class));
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=kafka", "APP_ROLE=matching")
                .run(context -> assertThat(context).doesNotHaveBean(DepartedMatchRequestScheduler.class));
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=kafka", "APP_ROLE=rest")
                .run(context -> assertThat(context).doesNotHaveBean(DepartedMatchRequestScheduler.class));
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않아 다음 주기가 계속 돈다")
    void swallowsFailure() {
        DepartedMatchRequestService service = mock(DepartedMatchRequestService.class);
        given(service.failDeparted()).willThrow(new IllegalStateException("DB 연결 실패"));

        assertThatCode(() -> new DepartedMatchRequestScheduler(service).sweep()).doesNotThrowAnyException();
    }
}
