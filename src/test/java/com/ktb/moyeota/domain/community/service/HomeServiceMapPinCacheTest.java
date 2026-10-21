package com.ktb.moyeota.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.repository.CommunityPinProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.community.service.cache.CellPins;
import com.ktb.moyeota.domain.community.service.cache.MapPinCellCache;
import com.ktb.moyeota.domain.community.service.cache.MapPinCellLoader;
import com.ktb.moyeota.domain.community.service.grid.GridCell;
import com.ktb.moyeota.domain.community.service.grid.MapGrid;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPinProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import com.ktb.moyeota.global.external.s3.S3Properties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import(RedisTestContainerConfig.class)
@ExtendWith(MockitoExtension.class)
class HomeServiceMapPinCacheTest {

    private static final MapPinSearchRequest GANGNAM = request("37.495", "127.025", "37.505", "127.035");
    private static final MapPinSearchRequest GANGNAM_DRAGGED = request("37.495", "127.028", "37.505", "127.038");
    private static final GridCell SOUTH_WEST = new GridCell(0, 3749, 12702);
    private static final GridCell SOUTH_EAST = new GridCell(0, 3749, 12703);
    private static final GridCell NORTH_WEST = new GridCell(0, 3750, 12702);
    private static final GridCell NORTH_EAST = new GridCell(0, 3750, 12703);
    private static final BigDecimal SOUTH_WEST_MIN_LAT = new BigDecimal("37.490000");
    private static final BigDecimal SOUTH_WEST_MIN_LNG = new BigDecimal("127.020000");

    private final MapGrid mapGrid = new MapGrid(10_000, 6, 4);
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CompanionPostRepository companionPostRepository;

    @Mock
    private NearbyPostCursorCodec cursorCodec;

    private MapPinCellCache cache;
    private HomeService service;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("map-pins:cell:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        cache = new MapPinCellCache(redisTemplate, jsonMapper, new SimpleMeterRegistry());
        service = new HomeService(communityPostRepository, companionPostRepository, cursorCodec,
                new ImageUrlResolver(new S3Properties(
                        "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test")),
                mapGrid, cache, new MapPinCellLoader(mapGrid, communityPostRepository, companionPostRepository));
    }

    @Test
    @DisplayName("빈 칸은 DB에서 채워 Redis에 저장하고, 조금 민 화면은 DB 없이 같은 칸으로 답한다")
    void fillsMissedCellsThenServesDraggedViewportFromCache() {
        CommunityPinProjection onlyPin = communityPin(1L, "37.4990", "127.0290");
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> SOUTH_WEST_MIN_LAT.equals(invocation.getArgument(0))
                        && SOUTH_WEST_MIN_LNG.equals(invocation.getArgument(1)) ? List.of(onlyPin) : List.of());
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());

        MapPinSearchResponse first = service.searchMapPins(GANGNAM);

        assertThat(first.limitExceeded()).isFalse();
        assertThat(first.items()).extracting(MapPinItem::id).containsExactly(1L);
        assertThat(cache.readCells(List.of(SOUTH_WEST, SOUTH_EAST, NORTH_WEST, NORTH_EAST)).orElseThrow())
                .hasSize(4);

        lenient().when(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .thenThrow(new QueryTimeoutException("DB에 가면 안 된다"));

        MapPinSearchResponse dragged = service.searchMapPins(GANGNAM_DRAGGED);

        assertThat(dragged.limitExceeded()).isFalse();
        assertThat(dragged.items()).extracting(MapPinItem::id).containsExactly(1L);
    }

    @Test
    @DisplayName("칸에 있어도 화면 밖 핀은 빼고, 타입과 id 순으로 정렬한다")
    void filtersOutsideAndSorts() {
        cache.writeCell(SOUTH_WEST, CellPins.of(List.of(
                pin(MapPinType.COMPANION, 7L, "37.4980", "127.0280"),
                pin(MapPinType.COMMUNITY, 9L, "37.4990", "127.0290"),
                pin(MapPinType.COMMUNITY, 3L, "37.4960", "127.0260"),
                pin(MapPinType.COMMUNITY, 5L, "37.4920", "127.0290"))));
        cache.writeCell(SOUTH_EAST, CellPins.of(List.of()));
        cache.writeCell(NORTH_WEST, CellPins.of(List.of()));
        cache.writeCell(NORTH_EAST, CellPins.of(List.of()));

        MapPinSearchResponse response = service.searchMapPins(GANGNAM);

        assertThat(response.items()).extracting(MapPinItem::type, MapPinItem::id).containsExactly(
                tuple(MapPinType.COMMUNITY, 3L),
                tuple(MapPinType.COMMUNITY, 9L),
                tuple(MapPinType.COMPANION, 7L));
    }

    @Test
    @DisplayName("화면 안에 완전히 들어간 칸이 넘쳤으면 DB 없이 limitExceeded=true다")
    void fullyInsideOverflowIsExceeded() {
        MapPinSearchRequest wide = request("37.495", "127.025", "37.535", "127.045");
        cache.writeCell(new GridCell(0, 3750, 12703), CellPins.overflowed());
        lenient().when(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .thenThrow(new QueryTimeoutException("DB에 가면 안 된다"));

        MapPinSearchResponse response = service.searchMapPins(wide);

        assertThat(response.limitExceeded()).isTrue();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("넘친 칸이 화면에 일부만 걸쳤으면 화면 쿼리 결과로 답한다")
    void partlyOverflowFallsBackToViewportQuery() {
        cache.writeCell(SOUTH_WEST, CellPins.overflowed());
        cache.writeCell(SOUTH_EAST, CellPins.of(List.of()));
        cache.writeCell(NORTH_WEST, CellPins.of(List.of()));
        cache.writeCell(NORTH_EAST, CellPins.of(List.of()));
        CommunityPinProjection viewportPin = communityPin(11L, "37.4970", "127.0270");
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(viewportPin));
        given(companionPostRepository.findPinsInViewport(any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());

        MapPinSearchResponse response = service.searchMapPins(GANGNAM);

        assertThat(response.limitExceeded()).isFalse();
        assertThat(response.items()).extracting(MapPinItem::id).containsExactly(11L);
    }

    @Test
    @DisplayName("칸마다 500개 이하여도 화면 안 핀이 합쳐서 500개를 넘으면 limitExceeded=true다")
    void exceedsWhenCellsSumOverLimit() {
        cache.writeCell(SOUTH_WEST, CellPins.of(pinsInside(1L, 300, "37.4980", "127.0280")));
        cache.writeCell(SOUTH_EAST, CellPins.of(pinsInside(1_001L, 201, "37.4980", "127.0310")));
        cache.writeCell(NORTH_WEST, CellPins.of(List.of()));
        cache.writeCell(NORTH_EAST, CellPins.of(List.of()));

        MapPinSearchResponse response = service.searchMapPins(GANGNAM);

        assertThat(response.limitExceeded()).isTrue();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("DB에서 채운 칸이 두 타입 합쳐 500개를 넘으면 넘친 칸으로 저장한다")
    void storesOverflowedCellWhenLoadedOverLimit() {
        List<CommunityPinProjection> crowded = new ArrayList<>();
        for (long id = 1; id <= 300; id++) {
            crowded.add(mock(CommunityPinProjection.class));
        }
        List<CompanionPinProjection> companions = new ArrayList<>();
        for (long id = 1; id <= 201; id++) {
            companions.add(mock(CompanionPinProjection.class));
        }
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(crowded);
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(companions);
        cache.writeCell(SOUTH_EAST, CellPins.of(List.of()));
        cache.writeCell(NORTH_WEST, CellPins.of(List.of()));
        cache.writeCell(NORTH_EAST, CellPins.of(List.of()));
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any(), anyInt())).willReturn(List.of());
        given(companionPostRepository.findPinsInViewport(any(), any(), any(), any(), anyInt())).willReturn(List.of());

        service.searchMapPins(GANGNAM);

        assertThat(cache.readCells(List.of(SOUTH_WEST)).orElseThrow().get(SOUTH_WEST).overflow()).isTrue();
    }

    private static MapPinSearchRequest request(String swLat, String swLng, String neLat, String neLng) {
        return new MapPinSearchRequest(
                new BigDecimal(swLat), new BigDecimal(swLng), new BigDecimal(neLat), new BigDecimal(neLng));
    }

    private static MapPinItem pin(MapPinType type, Long id, String lat, String lng) {
        return new MapPinItem(type, id, new BigDecimal(lat), new BigDecimal(lng));
    }

    private static List<MapPinItem> pinsInside(long firstId, int count, String lat, String lng) {
        List<MapPinItem> pins = new ArrayList<>(count);
        for (long id = firstId; id < firstId + count; id++) {
            pins.add(pin(MapPinType.COMMUNITY, id, lat, lng));
        }
        return pins;
    }

    private static CommunityPinProjection communityPin(Long id, String lat, String lng) {
        CommunityPinProjection pin = mock(CommunityPinProjection.class);
        given(pin.getId()).willReturn(id);
        given(pin.getLat()).willReturn(new BigDecimal(lat));
        given(pin.getLng()).willReturn(new BigDecimal(lng));
        return pin;
    }
}
