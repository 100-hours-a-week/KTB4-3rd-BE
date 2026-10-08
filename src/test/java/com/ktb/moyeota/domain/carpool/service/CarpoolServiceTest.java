package com.ktb.moyeota.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.carpool.model.CarpoolPin;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.repository.CarpoolPinProjection;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
import com.ktb.moyeota.global.common.Viewport;
import java.math.BigDecimal;
import java.time.Clock;
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
    private static final Viewport VIEWPORT = new Viewport(
            new BigDecimal("37.39"), new BigDecimal("127.10"), new BigDecimal("37.40"), new BigDecimal("127.12"));

    @Mock
    private CarpoolRepository carpoolRepository;

    private CarpoolService service;

    @BeforeEach
    void setUp() {
        service = new CarpoolService(carpoolRepository, Clock.fixed(NOW, SEOUL));
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
