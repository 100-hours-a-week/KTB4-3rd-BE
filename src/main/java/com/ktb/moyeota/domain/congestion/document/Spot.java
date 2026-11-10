package com.ktb.moyeota.domain.congestion.document;

import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("spots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot {

    @Id
    private Long id;

    private SpotType type;

    private String name;

    private GeoJsonPoint location;

    @Field("last_signal_at")
    private Instant lastSignalAt;

    private CongestionStatus status;

    @Field("congestion_level")
    private CongestionLevel congestionLevel;

    private List<CauseReport> causes;

    private String summary;

    @Field("report_count")
    private Integer reportCount;

    @Field("calculated_at")
    private Instant calculatedAt;

    @Field("valid_until")
    private Instant validUntil;

    @Field("pending_window_end")
    private Instant pendingWindowEnd;

    @Field("result_window_end")
    private Instant resultWindowEnd;
}
