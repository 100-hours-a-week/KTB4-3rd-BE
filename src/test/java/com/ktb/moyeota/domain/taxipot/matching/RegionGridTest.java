package com.ktb.moyeota.domain.taxipot.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionGridTest {

    private static final GeoPoint PANGYO = new GeoPoint(37_394_726, 127_111_209);
    private static final GeoPoint GANGNAM = new GeoPoint(37_498_086, 127_028_001);
    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    private final RegionGrid grid = new RegionGrid(2_000);

    @Test
    @DisplayName("칸의 남북 길이는 2km다")
    void cellIsTwoKilometersNorthSouth() {
        GeoPoint rowStart = firstPointOfNextCell(PANGYO, point -> new GeoPoint(point.latMicros() + 1, point.lngMicros()));
        GeoPoint nextRowStart = firstPointOfNextCell(rowStart, point -> new GeoPoint(point.latMicros() + 1, point.lngMicros()));

        assertThat(GeoDistance.meters(rowStart, nextRowStart)).isBetween(1_999.0, 2_001.0);
    }

    @Test
    @DisplayName("서울 위도에서 칸의 동서 길이는 2km다")
    void cellIsTwoKilometersEastWestAroundSeoul() {
        GeoPoint colStart = firstPointOfNextCell(GANGNAM, point -> new GeoPoint(point.latMicros(), point.lngMicros() + 1));
        GeoPoint nextColStart = firstPointOfNextCell(colStart, point -> new GeoPoint(point.latMicros(), point.lngMicros() + 1));

        assertThat(GeoDistance.meters(colStart, nextColStart)).isBetween(1_999.0, 2_001.0);
    }

    @Test
    @DisplayName("출발지가 같은 칸이고 출발 시각이 같으면 도착지와 관계없이 같은 묶음이다")
    void sameBatchForSameOriginCellAndDeparture() {
        BatchKey toGangnam = grid.batchKeyOf(request(PANGYO, GANGNAM, DEPARTURE_AT));
        BatchKey toPangyo = grid.batchKeyOf(request(PANGYO, PANGYO, DEPARTURE_AT));

        assertThat(toGangnam).isEqualTo(toPangyo);
    }

    @Test
    @DisplayName("출발지가 같은 칸이어도 출발 시각이 다르면 다른 묶음이다")
    void differentBatchForDifferentDeparture() {
        BatchKey nine = grid.batchKeyOf(request(PANGYO, GANGNAM, DEPARTURE_AT));
        BatchKey nineThirty = grid.batchKeyOf(request(PANGYO, GANGNAM, DEPARTURE_AT.plusMinutes(30)));

        assertThat(nine).isNotEqualTo(nineThirty);
    }

    @Test
    @DisplayName("칸 경계를 사이에 두면 1m 떨어져도 다른 묶음이다")
    void differentBatchAcrossCellBoundary() {
        GeoPoint boundary = firstPointOfNextCell(PANGYO, point -> new GeoPoint(point.latMicros() + 1, point.lngMicros()));
        GeoPoint justSouth = new GeoPoint(boundary.latMicros() - 9, boundary.lngMicros());

        assertThat(GeoDistance.meters(boundary, justSouth)).isLessThan(1.1);
        assertThat(grid.batchKeyOf(request(boundary, GANGNAM, DEPARTURE_AT)))
                .isNotEqualTo(grid.batchKeyOf(request(justSouth, GANGNAM, DEPARTURE_AT)));
    }

    private GeoPoint firstPointOfNextCell(GeoPoint from, UnaryOperator<GeoPoint> step) {
        RegionCell start = grid.cellOf(from);
        GeoPoint point = from;
        while (grid.cellOf(point).equals(start)) {
            point = step.apply(point);
        }
        return point;
    }

    private MatchRequest request(GeoPoint origin, GeoPoint dest, LocalDateTime departureAt) {
        return new MatchRequest(1, 1, new Place("출발지", origin), new Place("도착지", dest), departureAt,
                departureAt.minusMinutes(30));
    }
}
