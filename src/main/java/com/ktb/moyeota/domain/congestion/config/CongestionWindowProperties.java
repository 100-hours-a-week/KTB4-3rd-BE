package com.ktb.moyeota.domain.congestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.congestion.window")
public record CongestionWindowProperties(
        @DefaultValue("7") int morningStartHour,
        @DefaultValue("10") int morningEndHour,
        @DefaultValue("17") int eveningStartHour,
        @DefaultValue("20") int eveningEndHour) {

    public CongestionWindowProperties {
        if (morningStartHour < 0
                || morningStartHour >= morningEndHour
                || morningEndHour > eveningStartHour
                || eveningStartHour >= eveningEndHour
                || eveningEndHour > 24) {
            throw new IllegalArgumentException("출퇴근 시간대는 0~24시 안에서 출근, 퇴근 순으로 겹치지 않아야 합니다");
        }
    }

    public boolean isCommuteHour(int hour) {
        return (morningStartHour <= hour && hour < morningEndHour)
                || (eveningStartHour <= hour && hour < eveningEndHour);
    }
}
