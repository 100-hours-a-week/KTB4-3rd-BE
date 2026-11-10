package com.ktb.moyeota.domain.taxipot.matching;

public class RegionGrid {

    private static final double METERS_PER_DEGREE_LAT = 111_195;
    private static final double REFERENCE_LAT_DEGREES = 37.5;
    private static final double MICROS_PER_DEGREE = 1_000_000;

    private final long latCellMicros;
    private final long lngCellMicros;

    public RegionGrid(int cellMeters) {
        double latCellDegrees = cellMeters / METERS_PER_DEGREE_LAT;
        double lngCellDegrees = latCellDegrees / Math.cos(Math.toRadians(REFERENCE_LAT_DEGREES));
        this.latCellMicros = Math.round(latCellDegrees * MICROS_PER_DEGREE);
        this.lngCellMicros = Math.round(lngCellDegrees * MICROS_PER_DEGREE);
    }

    public RegionCell cellOf(GeoPoint point) {
        return new RegionCell(
                Math.floorDiv(point.latMicros(), latCellMicros),
                Math.floorDiv(point.lngMicros(), lngCellMicros));
    }

    public BatchKey batchKeyOf(MatchRequest request) {
        return new BatchKey(cellOf(request.origin().point()), request.departureAt());
    }
}
