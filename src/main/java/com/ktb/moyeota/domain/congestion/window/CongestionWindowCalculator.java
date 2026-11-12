package com.ktb.moyeota.domain.congestion.window;

import com.ktb.moyeota.domain.congestion.config.CongestionWindowProperties;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CongestionWindowCalculator {

    private static final ZoneId WINDOW_ZONE = ZoneId.of("Asia/Seoul");

    private final CongestionWindowProperties properties;

    public CongestionWindow windowContaining(Instant time) {
        ZonedDateTime local = time.atZone(WINDOW_ZONE);
        int minutes = windowMinutes(local);
        ZonedDateTime start = local.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes(local.getMinute() / minutes * minutes);
        return new CongestionWindow(start.toOffsetDateTime(), start.plusMinutes(minutes).toOffsetDateTime());
    }

    private int windowMinutes(ZonedDateTime time) {
        if (isWorkday(time.toLocalDate()) && properties.isCommuteHour(time.getHour())) {
            return properties.commuteMinutes();
        }
        return properties.defaultMinutes();
    }

    private static boolean isWorkday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
