package com.ktb.moyeota.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.carpool.model.CarpoolPin;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpool;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpoolCursor;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpoolQuery;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpools;
import com.ktb.moyeota.domain.carpool.repository.CarpoolPinProjection;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
import com.ktb.moyeota.domain.carpool.repository.NearbyCarpoolProjection;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CarpoolServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final LocalDateTime TEN_MINUTES_AGO = LocalDateTime.of(2026, 10, 7, 8, 50);
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.of(2026, 10, 7, 9, 0);
    private static final Viewport VIEWPORT = new Viewport(
            new BigDecimal("37.39"), new BigDecimal("127.10"), new BigDecimal("37.40"), new BigDecimal("127.12"));
    private static final BigDecimal USER_LAT = new BigDecimal("37.3947");
    private static final BigDecimal USER_LNG = new BigDecimal("127.1111");

    @Mock
    private CarpoolRepository carpoolRepository;

    private final NearbyCarpoolCursorCodec cursorCodec = new NearbyCarpoolCursorCodec();

    private CarpoolService service;

    @BeforeEach
    void setUp() {
        ImageUrlResolver imageUrlResolver = new ImageUrlResolver(new S3Properties(
                "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test"));
        service = new CarpoolService(carpoolRepository, cursorCodec, imageUrlResolver, Clock.fixed(NOW, SEOUL));
    }

    @Test
    @DisplayName("10분 전 시각을 기준으로 조회한 카풀을 출발지 좌표 핀으로 돌려준다")
    void findsPins() {
        givenPins(List.of(pin(51L, "37.3945", "127.1112"), pin(77L, "37.4011", "127.1088")));

        CarpoolPins result = service.findPins(VIEWPORT);

        assertThat(result.limitExceeded()).isFalse();
        assertThat(result.limit()).isEqualTo(500);
        assertThat(result.pins()).containsExactly(
                new CarpoolPin(51L, new BigDecimal("37.3945"), new BigDecimal("127.1112")),
                new CarpoolPin(77L, new BigDecimal("37.4011"), new BigDecimal("127.1088")));
    }

    @Test
    @DisplayName("핀이 상한인 500개면 모두 돌려준다")
    void returnsPinsAtLimit() {
        givenPins(pins(500));

        CarpoolPins result = service.findPins(VIEWPORT);

        assertThat(result.limitExceeded()).isFalse();
        assertThat(result.pins()).hasSize(500);
    }

    @Test
    @DisplayName("핀이 500개를 넘으면 빈 목록과 limitExceeded를 돌려준다")
    void returnsEmptyOverLimit() {
        givenPins(pins(501));

        CarpoolPins result = service.findPins(VIEWPORT);

        assertThat(result.limitExceeded()).isTrue();
        assertThat(result.pins()).isEmpty();
    }

    @Test
    @DisplayName("첫 페이지는 커서 없이 조회해 주변 카풀을 거리 · 방장 · 인원 정보와 함께 돌려준다")
    void findsNearbyFirstPage() {
        givenNearby(null, null, List.of(nearby(51L, 120.5, NOW_LOCAL.plusMinutes(30), 2, 4, "profile/host.png")));

        NearbyCarpools result = service.findNearby(new NearbyCarpoolQuery(USER_LAT, USER_LNG, VIEWPORT, null));

        assertThat(result.nextCursor()).isNull();
        assertThat(result.carpools()).containsExactly(new NearbyCarpool(
                51L, "방장51", "https://cdn.moyeota.test/profile/host.png", "판교역", "강남역",
                NOW_LOCAL.plusMinutes(30), 120.5, 2, 4, false, false));
    }

    @Test
    @DisplayName("정원이 찼으면 full, 출발 시각이 지났으면 expired 다")
    void marksFullAndExpired() {
        givenNearby(null, null, List.of(
                nearby(51L, 100.0, NOW_LOCAL.minusMinutes(1), 4, 4, null),
                nearby(52L, 200.0, NOW_LOCAL, 3, 4, null)));

        NearbyCarpools result = service.findNearby(new NearbyCarpoolQuery(USER_LAT, USER_LNG, VIEWPORT, null));

        assertThat(result.carpools()).extracting(NearbyCarpool::id, NearbyCarpool::full, NearbyCarpool::expired)
                .containsExactly(
                        tuple(51L, true, true),
                        tuple(52L, false, false));
        assertThat(result.carpools().getFirst().hostProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("11건이 오면 10건만 돌려주고 마지막 카풀의 거리와 id로 다음 커서를 만든다")
    void makesNextCursorWhenMoreRemain() {
        List<NearbyCarpoolProjection> rows = new ArrayList<>();
        for (long id = 1; id <= 11; id++) {
            rows.add(nearby(id, id * 10.0, NOW_LOCAL.plusMinutes(30), 1, 4, null));
        }
        givenNearby(null, null, rows);

        NearbyCarpools result = service.findNearby(new NearbyCarpoolQuery(USER_LAT, USER_LNG, VIEWPORT, null));

        assertThat(result.carpools()).hasSize(10);
        assertThat(cursorCodec.decode(result.nextCursor())).contains(new NearbyCarpoolCursor(100.0, 10L));
    }

    @Test
    @DisplayName("다음 페이지는 커서의 거리와 id 다음부터 조회한다")
    void continuesFromCursor() {
        String cursor = cursorCodec.encode(new NearbyCarpoolCursor(100.0, 10L));
        givenNearby(100.0, 10L, List.of(nearby(11L, 110.0, NOW_LOCAL.plusMinutes(30), 1, 4, null)));

        NearbyCarpools result = service.findNearby(new NearbyCarpoolQuery(USER_LAT, USER_LNG, VIEWPORT, cursor));

        assertThat(result.carpools()).extracting(NearbyCarpool::id).containsExactly(11L);
        assertThat(result.nextCursor()).isNull();
    }

    private void givenPins(List<CarpoolPinProjection> rows) {
        given(carpoolRepository.findPinsInViewport(
                eq(VIEWPORT.swLat()), eq(VIEWPORT.swLng()), eq(VIEWPORT.neLat()), eq(VIEWPORT.neLng()),
                eq(TEN_MINUTES_AGO), anyInt()))
                .willReturn(rows);
    }

    private static List<CarpoolPinProjection> pins(int count) {
        List<CarpoolPinProjection> rows = new ArrayList<>(count);
        for (long id = 1; id <= count; id++) {
            rows.add(pin(id, "37.395", "127.111"));
        }
        return rows;
    }

    private void givenNearby(Double cursorDistance, Long cursorId, List<NearbyCarpoolProjection> rows) {
        given(carpoolRepository.findNearby(
                eq(USER_LAT), eq(USER_LNG),
                eq(VIEWPORT.swLat()), eq(VIEWPORT.swLng()), eq(VIEWPORT.neLat()), eq(VIEWPORT.neLng()),
                eq(cursorDistance), eq(cursorId), eq(11)))
                .willReturn(rows);
    }

    private static NearbyCarpoolProjection nearby(
            Long id, double distanceM, LocalDateTime departureAt, int currentCount, int capacity, String profileKey) {
        return new NearbyRow(id, "방장" + id, profileKey, "판교역", "강남역", departureAt, distanceM, currentCount, capacity);
    }

    private record NearbyRow(
            Long id, String hostName, String hostProfileImageUrl, String originName, String destName,
            LocalDateTime departureAt, Double distanceM, Integer currentCount, Integer capacity)
            implements NearbyCarpoolProjection {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getHostName() {
            return hostName;
        }

        @Override
        public String getHostProfileImageUrl() {
            return hostProfileImageUrl;
        }

        @Override
        public String getOriginName() {
            return originName;
        }

        @Override
        public String getDestName() {
            return destName;
        }

        @Override
        public LocalDateTime getDepartureAt() {
            return departureAt;
        }

        @Override
        public Double getDistanceM() {
            return distanceM;
        }

        @Override
        public Integer getCurrentCount() {
            return currentCount;
        }

        @Override
        public Integer getCapacity() {
            return capacity;
        }
    }

    private static CarpoolPinProjection pin(Long id, String lat, String lng) {
        return new PinRow(id, new BigDecimal(lat), new BigDecimal(lng));
    }

    private record PinRow(Long id, BigDecimal originLat, BigDecimal originLng) implements CarpoolPinProjection {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public BigDecimal getOriginLat() {
            return originLat;
        }

        @Override
        public BigDecimal getOriginLng() {
            return originLng;
        }
    }
}
