package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class CommuteScenarios {

    public static final Map<PlaceGroup, Double> STATION_SIDE =
            Map.of(PlaceGroup.STATION, 0.6, PlaceGroup.BUILDING, 0.3, PlaceGroup.OTHER, 0.1);
    public static final Map<PlaceGroup, Double> OFFICE_SIDE =
            Map.of(PlaceGroup.BUILDING, 0.85, PlaceGroup.OTHER, 0.15);

    private static final LocalTime MORNING_FROM = LocalTime.of(7, 30);
    private static final LocalTime MORNING_TO = LocalTime.of(9, 30);
    private static final LocalTime EVENING_FROM = LocalTime.of(18, 0);
    private static final LocalTime EVENING_TO = LocalTime.of(20, 0);
    private static final int STATION_RADIUS_METERS = 300;

    public static final Spot PANGYO_STATION = station("판교역", "37.394778", "127.111200");
    public static final Spot PANGYO_TECHNO_VALLEY = office("판교 테크노밸리", "37.408500", "127.100500", 1_200);
    public static final Spot GASAN_STATION = station("가산디지털단지역", "37.480961", "126.882638");
    public static final Spot G_VALLEY = office("가산·구로 G밸리", "37.480000", "126.890000", 1_300);
    public static final Spot MAGOKNARU_STATION = station("마곡나루역", "37.566713", "126.827421");
    public static final Spot MAGOK_RND = office("마곡 R&D 단지", "37.562000", "126.833000", 900);
    public static final Spot GANGNAM_STATION = station("강남역", "37.497432", "127.028093");

    private CommuteScenarios() {
    }

    public static List<Scenario> all() {
        List<Scenario> morning = List.of(
                morning("판교 출근", PANGYO_STATION, PANGYO_TECHNO_VALLEY),
                morning("가산 출근", GASAN_STATION, G_VALLEY),
                morning("마곡 출근", MAGOKNARU_STATION, MAGOK_RND),
                morning("강남→판교 광역 출근", GANGNAM_STATION, PANGYO_TECHNO_VALLEY));
        List<Scenario> evening = List.of(
                morning.get(0).reversed("판교 퇴근", EVENING_FROM, EVENING_TO),
                morning.get(1).reversed("가산 퇴근", EVENING_FROM, EVENING_TO),
                morning.get(2).reversed("마곡 퇴근", EVENING_FROM, EVENING_TO),
                morning.get(3).reversed("판교→강남 광역 퇴근", EVENING_FROM, EVENING_TO));
        return Stream.concat(morning.stream(), evening.stream()).toList();
    }

    private static Scenario morning(String name, Spot origin, Spot dest) {
        return new Scenario(name, origin, dest, MORNING_FROM, MORNING_TO);
    }

    private static Spot station(String name, String lat, String lng) {
        return new Spot(name, point(lat, lng), STATION_RADIUS_METERS, STATION_SIDE);
    }

    private static Spot office(String name, String lat, String lng, int radiusMeters) {
        return new Spot(name, point(lat, lng), radiusMeters, OFFICE_SIDE);
    }

    private static GeoPoint point(String lat, String lng) {
        return GeoPoint.of(new BigDecimal(lat), new BigDecimal(lng));
    }
}
