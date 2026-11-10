package com.ktb.moyeota.domain.community.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapPinMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("만들자마자 화면 쿼리로 넘어간 이유마다 0인 카운터가 등록된다")
    void registersEveryFallbackReasonAtZero() {
        new MapPinMetrics(meterRegistry);

        for (ViewportFallbackReason reason : ViewportFallbackReason.values()) {
            assertThat(fallbackCount(reason)).isZero();
        }
    }

    @Test
    @DisplayName("화면 쿼리로 넘어가면 그 이유의 카운터가 오른다")
    void countsGivenReason() {
        MapPinMetrics metrics = new MapPinMetrics(meterRegistry);

        metrics.countViewportFallback(ViewportFallbackReason.CACHE_UNAVAILABLE);

        assertThat(fallbackCount(ViewportFallbackReason.CACHE_UNAVAILABLE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("만들자마자 가장 작은 칸까지 넘친 횟수와 내려간 깊이가 0으로 등록된다")
    void registersSplitMetricsAtZero() {
        new MapPinMetrics(meterRegistry);

        assertThat(meterRegistry.get(MapPinMetrics.FLOOR_OVERFLOWED).counter().count()).isZero();
        assertThat(meterRegistry.get(MapPinMetrics.SPLIT_DEPTH).summary().count()).isZero();
    }

    @Test
    @DisplayName("내려간 깊이를 요청마다 남긴다")
    void recordsSplitDepth() {
        MapPinMetrics metrics = new MapPinMetrics(meterRegistry);

        metrics.recordSplitDepth(0);
        metrics.recordSplitDepth(3);

        assertThat(meterRegistry.get(MapPinMetrics.SPLIT_DEPTH).summary().count()).isEqualTo(2);
        assertThat(meterRegistry.get(MapPinMetrics.SPLIT_DEPTH).summary().max()).isEqualTo(3.0);
    }

    private double fallbackCount(ViewportFallbackReason reason) {
        return meterRegistry.get(MapPinMetrics.VIEWPORT_FALLBACK)
                .tag(MapPinMetrics.REASON_TAG, reason.tagValue())
                .counter()
                .count();
    }
}
