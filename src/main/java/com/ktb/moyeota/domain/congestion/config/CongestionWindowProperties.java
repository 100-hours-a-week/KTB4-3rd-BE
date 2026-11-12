package com.ktb.moyeota.domain.congestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.congestion.window")
public record CongestionWindowProperties(
        @DefaultValue("7") int morningStartHour,
        @DefaultValue("10") int morningEndHour,
        @DefaultValue("17") int eveningStartHour,
        @DefaultValue("20") int eveningEndHour,
        @DefaultValue("10") int commuteMinutes,
        @DefaultValue("60") int defaultMinutes) {

    private static final int MINUTES_PER_HOUR = 60;

    public CongestionWindowProperties {
        if (morningStartHour < 0
                || morningStartHour >= morningEndHour
                || morningEndHour > eveningStartHour
                || eveningStartHour >= eveningEndHour
                || eveningEndHour > 24) {
            throw new IllegalArgumentException("출퇴근 시간대는 0~24시 안에서 출근, 퇴근 순으로 겹치지 않아야 합니다");
        }
        if (!dividesHour(commuteMinutes) || !dividesHour(defaultMinutes)) {
            throw new IllegalArgumentException("구간 길이는 60의 약수인 분이어야 합니다");
        }
    }

    public boolean isCommuteHour(int hour) {
        return (morningStartHour <= hour && hour < morningEndHour)
                || (eveningStartHour <= hour && hour < eveningEndHour);
    }

    private static boolean dividesHour(int minutes) {
        return minutes > 0 && MINUTES_PER_HOUR % minutes == 0;
    }
}
