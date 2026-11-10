package com.ktb.moyeota.domain.taxipot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.config.TaxiPotMatchingProperties.MatchingMode;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class TaxiPotMatchingPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(TaxiPotMatchingProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("설정하지 않으면 즉시 매칭과 기본값을 쓴다")
    void defaults() {
        runner.run(context -> {
            TaxiPotMatchingProperties properties = context.getBean(TaxiPotMatchingProperties.class);

            assertThat(properties.mode()).isEqualTo(MatchingMode.INSTANT);
            assertThat(properties.isBatch()).isFalse();
            assertThat(properties.queuesRequests()).isFalse();
            assertThat(properties.cellMeters()).isEqualTo(2_000);
            assertThat(properties.radiusMeters()).isEqualTo(100);
            assertThat(properties.neighborLimit()).isEqualTo(8);
            assertThat(properties.sweepDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(15));
        });
    }

    @Test
    @DisplayName("소문자 batch로 묶음 매칭을 켜고 값을 덮어쓸 수 있다")
    void override() {
        runner.withPropertyValues(
                        "moyeota.taxi-pot.matching.mode=batch",
                        "moyeota.taxi-pot.matching.cell-meters=1000",
                        "moyeota.taxi-pot.matching.radius-meters=50",
                        "moyeota.taxi-pot.matching.neighbor-limit=4")
                .run(context -> {
                    TaxiPotMatchingProperties properties = context.getBean(TaxiPotMatchingProperties.class);

                    assertThat(properties.isBatch()).isTrue();
                    assertThat(properties.cellMeters()).isEqualTo(1_000);
                    assertThat(properties.radiusMeters()).isEqualTo(50);
                    assertThat(properties.neighborLimit()).isEqualTo(4);
                });
    }

    @Test
    @DisplayName("kafka 방식도 요청을 쌓지만 RDB 묶음 매칭은 아니다")
    void kafkaQueuesRequests() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=kafka")
                .run(context -> {
                    TaxiPotMatchingProperties properties = context.getBean(TaxiPotMatchingProperties.class);

                    assertThat(properties.mode()).isEqualTo(MatchingMode.KAFKA);
                    assertThat(properties.queuesRequests()).isTrue();
                    assertThat(properties.isBatch()).isFalse();
                });
    }

    @Test
    @DisplayName("매칭 방식에 오타가 있으면 서버가 뜨지 않는다")
    void failsOnUnknownMode() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.mode=bach")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("칸 크기가 0 이하이면 서버가 뜨지 않는다")
    void failsOnInvalidCellMeters() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.cell-meters=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("담당 기한을 정하지 않으면 주기의 3배를 쓴다")
    void leaseDefaultsToThreeSweeps() {
        runner.withPropertyValues("moyeota.taxi-pot.matching.sweep-delay=5s")
                .run(context -> assertThat(context.getBean(TaxiPotMatchingProperties.class).leaseDuration())
                        .isEqualTo(Duration.ofSeconds(15)));
    }

    @Test
    @DisplayName("담당 기한이 주기의 2배보다 짧으면 서버가 뜨지 않는다")
    void failsOnLeaseShorterThanTwoSweeps() {
        runner.withPropertyValues(
                        "moyeota.taxi-pot.matching.sweep-delay=30s",
                        "moyeota.taxi-pot.matching.lease-duration=59s")
                .run(context -> assertThat(context).hasFailed());
    }
}
