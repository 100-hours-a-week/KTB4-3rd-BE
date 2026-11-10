package com.ktb.moyeota.domain.taxipot.matching.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ktb.moyeota.domain.taxipot.matching.GeoDistance;
import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestFlowGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);
    private static final GeoPoint STATION_CENTER = new GeoPoint(37_394_778, 127_111_200);
    private static final GeoPoint OFFICE_CENTER = new GeoPoint(37_408_500, 127_100_500);
    private static final Spot STATION = new Spot("역", STATION_CENTER, 300,
            Map.of(PlaceGroup.STATION, 0.6, PlaceGroup.BUILDING, 0.3, PlaceGroup.OTHER, 0.1));
    private static final Spot OFFICE = new Spot("회사", OFFICE_CENTER, 300,
            Map.of(PlaceGroup.BUILDING, 0.85, PlaceGroup.OTHER, 0.15));
    private static final Scenario MORNING = new Scenario(
            "출근", STATION, OFFICE, LocalTime.of(7, 30), LocalTime.of(9, 30));
    private static final List<SimPlace> PLACES = places();

    private final RequestFlowGenerator generator = new RequestFlowGenerator(PLACES, 1.0);

    @Test
    @DisplayName("같은 시드면 같은 요청 흐름이 나온다")
    void sameSeedSameFlow() {
        assertThat(generator.generate(MORNING, DAY, 500, 0.3, 7)).isEqualTo(generator.generate(MORNING, DAY, 500, 0.3, 7));
        assertThat(generator.generate(MORNING, DAY, 500, 0.3, 7)).isNotEqualTo(generator.generate(MORNING, DAY, 500, 0.3, 8));
    }

    @Test
    @DisplayName("요청 수만큼 시나리오 시간대 안에서 요청 시각 순서대로 번호를 붙여 만든다")
    void numbersRequestsInTimeOrderWithinWindow() {
        List<GeneratedRequest> flow = generator.generate(MORNING, DAY, 500, 0.3, 7);

        assertThat(flow).hasSize(500);
        for (int i = 0; i < flow.size(); i++) {
            MatchRequest request = flow.get(i).request();
            assertThat(request.requestId()).isEqualTo(i + 1);
            assertThat(request.requestedAt()).isBetween(
                    LocalDateTime.of(DAY, LocalTime.of(7, 30)), LocalDateTime.of(DAY, LocalTime.of(9, 30)));
        }
        assertThat(flow).isSortedAccordingTo(
                (left, right) -> left.request().requestedAt().compareTo(right.request().requestedAt()));
    }

    @Test
    @DisplayName("출발 시각은 30분 단위이고 요청 10분 뒤부터 90분 뒤 사이다")
    void departureIsHalfHourSlotAfterLead() {
        for (GeneratedRequest generated : generator.generate(MORNING, DAY, 500, 0.3, 7)) {
            MatchRequest request = generated.request();
            assertThat(request.departureAt().getMinute() % 30).isZero();
            assertThat(request.departureAt().getSecond()).isZero();
            assertThat(Duration.between(request.requestedAt(), request.departureAt()))
                    .isBetween(Duration.ofMinutes(10), Duration.ofMinutes(90));
        }
    }

    @Test
    @DisplayName("다르게 고르지 않으면 출발지는 출발 지점 반경, 도착지는 도착 지점 반경 안에서 고른다")
    void picksInsideSpotRadius() {
        assertThat(generator.generate(MORNING, DAY, 500, 0, 7)).allSatisfy(generated -> {
            assertThat(GeoDistance.meters(STATION_CENTER, generated.origin().point())).isLessThanOrEqualTo(300);
            assertThat(GeoDistance.meters(OFFICE_CENTER, generated.dest().point())).isLessThanOrEqualTo(300);
        });
    }

    @Test
    @DisplayName("다르게 고를 확률이 1이면 모든 요청이 원래 고를 장소에서 50m 안의 다른 장소를 고른다")
    void variantPicksAnotherPlaceWithinFiftyMeters() {
        List<GeneratedRequest> original = generator.generate(MORNING, DAY, 500, 0, 7);
        List<GeneratedRequest> varied = generator.generate(MORNING, DAY, 500, 1, 7);

        for (int i = 0; i < original.size(); i++) {
            assertVariant(original.get(i).origin(), varied.get(i).origin());
            assertVariant(original.get(i).dest(), varied.get(i).dest());
        }
    }

    @ParameterizedTest(name = "집중도 {0}")
    @ValueSource(doubles = {1.0, 2.0})
    @DisplayName("장소 묶음 비율은 집중도와 관계없이 정한 값을 따른다")
    void groupSharesHoldRegardlessOfConcentration(double exponent) {
        List<GeneratedRequest> flow = new RequestFlowGenerator(PLACES, exponent).generate(MORNING, DAY, 4_000, 0, 7);

        assertThat(share(flow, PlaceGroup.STATION)).isCloseTo(0.6, within(0.03));
        assertThat(share(flow, PlaceGroup.BUILDING)).isCloseTo(0.3, within(0.03));
        assertThat(flow).noneSatisfy(generated -> assertThat(PlaceGroup.of(generated.dest())).isEqualTo(PlaceGroup.STATION));
    }

    @Test
    @DisplayName("퇴근 시나리오는 출근의 출발 지점과 도착 지점을 바꾼다")
    void reversedSwapsSpots() {
        Scenario evening = MORNING.reversed("퇴근", LocalTime.of(18, 0), LocalTime.of(20, 0));

        assertThat(generator.generate(evening, DAY, 200, 0, 7)).allSatisfy(generated -> {
            assertThat(GeoDistance.meters(OFFICE_CENTER, generated.origin().point())).isLessThanOrEqualTo(300);
            assertThat(GeoDistance.meters(STATION_CENTER, generated.dest().point())).isLessThanOrEqualTo(300);
            assertThat(generated.request().requestedAt().getHour()).isBetween(18, 19);
        });
    }

    private double share(List<GeneratedRequest> flow, PlaceGroup group) {
        return (double) flow.stream().filter(generated -> PlaceGroup.of(generated.origin()) == group).count() / flow.size();
    }

    private void assertVariant(SimPlace original, SimPlace varied) {
        assertThat(varied.id()).isNotEqualTo(original.id());
        assertThat(GeoDistance.meters(original.point(), varied.point())).isLessThanOrEqualTo(50);
    }

    private static List<SimPlace> places() {
        List<SimPlace> places = new ArrayList<>();
        addPlaces(places, "역", STATION_CENTER, "SW8", 2);
        addPlaces(places, "역 건물", STATION_CENTER, null, 20);
        addPlaces(places, "역 식당", STATION_CENTER, "FD6", 60);
        addPlaces(places, "회사 건물", OFFICE_CENTER, null, 40);
        addPlaces(places, "회사 식당", OFFICE_CENTER, "FD6", 20);
        return places;
    }

    private static void addPlaces(List<SimPlace> places, String prefix, GeoPoint center, String category, int count) {
        for (int i = 0; i < count; i++) {
            GeoPoint point = new GeoPoint(center.latMicros() + (i % 10) * 100 - 450, center.lngMicros() + (i / 10) * 100);
            places.add(new SimPlace(prefix + i, prefix + i, category, prefix + " " + i, point, "시험"));
        }
    }
}
