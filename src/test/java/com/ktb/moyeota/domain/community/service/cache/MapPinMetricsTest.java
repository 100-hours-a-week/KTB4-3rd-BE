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
    @DisplayName("화면 쿼리로 넘어가면 그 이유의 카운터만 오른다")
    void countsOnlyGivenReason() {
        MapPinMetrics metrics = new MapPinMetrics(meterRegistry);

        metrics.countViewportFallback(ViewportFallbackReason.PARTLY_OVERFLOWED);

        assertThat(fallbackCount(ViewportFallbackReason.PARTLY_OVERFLOWED)).isEqualTo(1.0);
        assertThat(fallbackCount(ViewportFallbackReason.CACHE_UNAVAILABLE)).isZero();
    }

    private double fallbackCount(ViewportFallbackReason reason) {
        return meterRegistry.get(MapPinMetrics.VIEWPORT_FALLBACK)
                .tag(MapPinMetrics.REASON_TAG, reason.tagValue())
                .counter()
                .count();
    }
}
