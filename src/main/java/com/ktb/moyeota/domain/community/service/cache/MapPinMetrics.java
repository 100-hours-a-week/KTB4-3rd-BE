package com.ktb.moyeota.domain.community.service.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MapPinMetrics {

    public static final String VIEWPORT_FALLBACK = "map_pins.viewport_fallback";
    public static final String REASON_TAG = "reason";
    public static final String FLOOR_OVERFLOWED = "map_pins.floor_overflowed";
    public static final String SPLIT_DEPTH = "map_pins.split_depth";

    private final Map<ViewportFallbackReason, Counter> viewportFallbacks =
            new EnumMap<>(ViewportFallbackReason.class);
    private final Counter floorOverflowed;
    private final DistributionSummary splitDepth;

    public MapPinMetrics(MeterRegistry meterRegistry) {
        for (ViewportFallbackReason reason : ViewportFallbackReason.values()) {
            viewportFallbacks.put(reason, Counter.builder(VIEWPORT_FALLBACK)
                    .tag(REASON_TAG, reason.tagValue())
                    .register(meterRegistry));
        }
        floorOverflowed = Counter.builder(FLOOR_OVERFLOWED).register(meterRegistry);
        splitDepth = DistributionSummary.builder(SPLIT_DEPTH)
                .serviceLevelObjectives(1, 2, 3, 4)
                .register(meterRegistry);
    }

    public void countViewportFallback(ViewportFallbackReason reason) {
        viewportFallbacks.get(reason).increment();
    }

    public void countFloorOverflowed() {
        floorOverflowed.increment();
    }

    public void recordSplitDepth(int depth) {
        splitDepth.record(depth);
    }
}
