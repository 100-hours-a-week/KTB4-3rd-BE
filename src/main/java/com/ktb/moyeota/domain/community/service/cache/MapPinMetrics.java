package com.ktb.moyeota.domain.community.service.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MapPinMetrics {

    public static final String VIEWPORT_FALLBACK = "map_pins.viewport_fallback";
    public static final String REASON_TAG = "reason";

    private final Map<ViewportFallbackReason, Counter> viewportFallbacks =
            new EnumMap<>(ViewportFallbackReason.class);

    public MapPinMetrics(MeterRegistry meterRegistry) {
        for (ViewportFallbackReason reason : ViewportFallbackReason.values()) {
            viewportFallbacks.put(reason, Counter.builder(VIEWPORT_FALLBACK)
                    .tag(REASON_TAG, reason.tagValue())
                    .register(meterRegistry));
        }
    }

    public void countViewportFallback(ViewportFallbackReason reason) {
        viewportFallbacks.get(reason).increment();
    }
}
