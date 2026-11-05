package com.ktb.moyeota.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotItem;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchRequest;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

@ExtendWith(MockitoExtension.class)
class CongestionSpotListQueryTest {

    private static final Instant NOW = Instant.parse("2026-11-03T22:12:00Z");
    private static final CongestionSpotSearchRequest PANGYO_VIEW = new CongestionSpotSearchRequest(
            new BigDecimal("37.38"), new BigDecimal("127.10"), new BigDecimal("37.41"), new BigDecimal("127.13"));

    @Mock
    private CongestionLatestCache latestCache;

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private CongestionWindowCalculator calculator;

    private CongestionQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new CongestionQueryService(latestCache, spotRepository, calculator,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("캐시에 있으면 지도 범위 안 · 표시 기한 안 · 상태 있는 스팟만 돌려준다")
    void returnsFilteredSpotsFromCache() {
        given(latestCache.findAll()).willReturn(Optional.of(List.of(
                cached(102L, "정자역", 37.3670, 127.1083, CongestionStatus.SMOOTH, NOW.plusSeconds(480)),
                cached(101L, "판교역", 37.3948, 127.1112, CongestionStatus.CROWDED, NOW.plusSeconds(480)),
                cached(103L, "서현역", 37.3851, 127.1232, null, NOW.plusSeconds(480)),
                cached(104L, "이매역", 37.3954, 127.1279, CongestionStatus.CONGESTED, NOW.minusSeconds(1)),
                cached(201L, "강남역", 37.4979, 127.0276, CongestionStatus.ACCIDENT, NOW.plusSeconds(480)))));

        List<CongestionSpotItem> spots = queryService.findSpotsInBounds(PANGYO_VIEW).items();

        assertThat(spots).containsExactly(new CongestionSpotItem(101L, SpotType.STATION, "판교역", 37.3948, 127.1112,
                CongestionStatus.CROWDED, LocalDateTime.parse("2026-11-04T07:10:00")));
        verifyNoInteractions(spotRepository);
    }

    @Test
    @DisplayName("캐시가 비어 있으면 MongoDB 에서 읽어 캐시에 채운다")
    void loadsFromMongoAndFillsCacheWhenEmpty() {
        given(latestCache.findAll()).willReturn(Optional.of(List.of()));
        Spot pangyo = spot(101L, "판교역", 37.3948, 127.1112, CongestionStatus.CROWDED, NOW.plusSeconds(480));
        given(spotRepository.findAllLatest()).willReturn(List.of(pangyo));

        List<CongestionSpotItem> spots = queryService.findSpotsInBounds(PANGYO_VIEW).items();

        assertThat(spots).extracting(CongestionSpotItem::id).containsExactly(101L);
        verify(latestCache).putAll(argThat(list -> list.size() == 1 && list.getFirst().id().equals(101L)));
    }

    @Test
    @DisplayName("Redis 를 읽지 못하면 MongoDB 에서 읽고 캐시에는 쓰지 않는다")
    void loadsFromMongoWithoutFillingWhenCacheUnavailable() {
        given(latestCache.findAll()).willReturn(Optional.empty());
        Spot pangyo = spot(101L, "판교역", 37.3948, 127.1112, CongestionStatus.CROWDED, NOW.plusSeconds(480));
        given(spotRepository.findAllLatest()).willReturn(List.of(pangyo));

        List<CongestionSpotItem> spots = queryService.findSpotsInBounds(PANGYO_VIEW).items();

        assertThat(spots).extracting(CongestionSpotItem::id).containsExactly(101L);
        verify(latestCache, never()).putAll(anyList());
    }

    @Test
    @DisplayName("남서쪽이 북동쪽보다 크면 지도 범위 오류로 거부한다")
    void rejectsInvalidViewport() {
        CongestionSpotSearchRequest reversed = new CongestionSpotSearchRequest(
                new BigDecimal("37.41"), new BigDecimal("127.10"), new BigDecimal("37.38"), new BigDecimal("127.13"));

        assertThatThrownBy(() -> queryService.findSpotsInBounds(reversed)).isInstanceOf(BusinessException.class);
        verify(latestCache, never()).findAll();
        verify(spotRepository, never()).findAllLatest();
        verify(latestCache, never()).putAll(any());
    }

    private static CachedSpot cached(Long id, String name, double lat, double lng, CongestionStatus status,
            Instant validUntil) {
        return new CachedSpot(id, SpotType.STATION, name, lat, lng, status, CongestionLevel.HIGH, List.of(),
                "요약", 3, validUntil.minusSeconds(600), validUntil, null, validUntil.minusSeconds(600));
    }

    private static Spot spot(Long id, String name, double lat, double lng, CongestionStatus status,
            Instant validUntil) {
        Spot spot = mock(Spot.class);
        given(spot.getId()).willReturn(id);
        given(spot.getType()).willReturn(SpotType.STATION);
        given(spot.getName()).willReturn(name);
        given(spot.getLocation()).willReturn(new GeoJsonPoint(lng, lat));
        given(spot.getStatus()).willReturn(status);
        given(spot.getValidUntil()).willReturn(validUntil);
        return spot;
    }
}
