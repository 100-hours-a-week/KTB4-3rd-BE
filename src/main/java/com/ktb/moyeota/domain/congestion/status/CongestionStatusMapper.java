package com.ktb.moyeota.domain.congestion.status;

import com.ktb.moyeota.domain.congestion.config.CongestionStatusProperties;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.model.CongestionReport;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CongestionStatusMapper {

    private final CongestionStatusProperties properties;

    public Optional<CongestionStatus> statusOf(CongestionReport report) {
        if (report.reportersOf(CauseCode.ACCIDENT) >= properties.accidentReporterThreshold()) {
            return Optional.of(CongestionStatus.ACCIDENT);
        }
        if (report.level() == CongestionLevel.HIGH) {
            return Optional.of(CongestionStatus.CROWDED);
        }
        if (isDelayed(report)) {
            return Optional.of(CongestionStatus.DELAYED);
        }
        return switch (report.level()) {
            case MEDIUM -> Optional.of(CongestionStatus.CONGESTED);
            case LOW -> Optional.of(CongestionStatus.SMOOTH);
            case HIGH, UNKNOWN -> Optional.empty();
        };
    }

    private boolean isDelayed(CongestionReport report) {
        int threshold = properties.delayReporterThreshold();
        return report.reportersOf(CauseCode.DELAY_ISSUE) >= threshold
                || report.reportersOf(CauseCode.SERVICE_ISSUE) >= threshold;
    }
}
