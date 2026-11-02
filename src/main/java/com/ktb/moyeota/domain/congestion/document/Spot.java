package com.ktb.moyeota.domain.congestion.document;

import java.time.Instant;
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
}
