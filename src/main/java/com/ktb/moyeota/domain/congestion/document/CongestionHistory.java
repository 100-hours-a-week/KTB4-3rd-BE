package com.ktb.moyeota.domain.congestion.document;

import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("congestion_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CongestionHistory {

    @Id
    private String id;

    @Field("spot_id")
    private Long spotId;

    @Field("calculated_at")
    private Instant calculatedAt;

    @Field("window_minutes")
    private int windowMinutes;

    private CongestionStatus status;

    @Field("congestion_level")
    private CongestionLevel congestionLevel;

    private List<CauseReport> causes;

    private String summary;

    @Field("report_count")
    private int reportCount;

    private List<Object> evidence;

    private CongestionHistory(Long spotId, Instant calculatedAt, int windowMinutes, CongestionStatus status,
            CongestionLevel congestionLevel, List<CauseReport> causes, String summary, int reportCount) {
        this.spotId = spotId;
        this.calculatedAt = calculatedAt;
        this.windowMinutes = windowMinutes;
        this.status = status;
        this.congestionLevel = congestionLevel;
        this.causes = List.copyOf(causes);
        this.summary = summary;
        this.reportCount = reportCount;
        this.evidence = List.of();
    }

    public static CongestionHistory of(Long spotId, Instant calculatedAt, int windowMinutes, CongestionStatus status,
            CongestionLevel congestionLevel, List<CauseReport> causes, String summary, int reportCount) {
        if (spotId == null || calculatedAt == null) {
            throw new IllegalArgumentException("이력에는 spot_id 와 calculated_at 이 필요합니다");
        }
        return new CongestionHistory(spotId, calculatedAt, windowMinutes, status, congestionLevel, causes, summary,
                reportCount);
    }
}
