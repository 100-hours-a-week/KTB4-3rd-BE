package com.ktb.moyeota.domain.community.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.community.service.grid.GridCell;
import com.ktb.moyeota.domain.community.service.grid.MapGrid;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
class MapPinCellLoaderSingleFlightTest {

    private static final GridCell GANGNAM = new GridCell(0, 3749, 12702);
    private static final GridCell NEXT_TO_GANGNAM = new GridCell(0, 3749, 12703);
    private static final int CONCURRENT_REQUESTS = 20;

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CompanionPostRepository companionPostRepository;

    private final AtomicInteger dbQueries = new AtomicInteger();
    private final CountDownLatch releaseQuery = new CountDownLatch(1);
    private ExecutorService executor;
    private SimpleMeterRegistry meterRegistry;
    private MapPinCellCache cache;
    private MapPinCellLoader loader;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys(MapPinCellCache.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        meterRegistry = new SimpleMeterRegistry();
        cache = new MapPinCellCache(redisTemplate, jsonMapper, meterRegistry);
        loader = new MapPinCellLoader(
                new MapGrid(10_000, 6, 4, 4), communityPostRepository, companionPostRepository, cache);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("같은 칸을 여러 요청이 동시에 채우면 DB는 한 번만 읽고 모두 같은 결과를 받는다")
    void sameCellLoadsOnce() throws Exception {
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> {
                    dbQueries.incrementAndGet();
                    releaseQuery.await(5, TimeUnit.SECONDS);
                    return List.of();
                });
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(List.of());

        List<Future<CellPins>> results = loadConcurrently(GANGNAM, CONCURRENT_REQUESTS);
        releaseQuery.countDown();

        for (Future<CellPins> result : results) {
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(CellPins.of(List.of()));
        }
        assertThat(dbQueries).hasValue(1);
        assertThat(cache.readCells(List.of(GANGNAM)).orElseThrow()).containsEntry(GANGNAM, CellPins.of(List.of()));
    }

    @Test
    @DisplayName("서로 다른 칸은 기다리지 않고 칸마다 따로 읽는다")
    void differentCellsLoadSeparately() throws Exception {
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> {
                    dbQueries.incrementAndGet();
                    releaseQuery.await(5, TimeUnit.SECONDS);
                    return List.of();
                });
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(List.of());

        List<Future<CellPins>> results = new ArrayList<>();
        results.addAll(loadConcurrently(GANGNAM, 5));
        results.addAll(loadConcurrently(NEXT_TO_GANGNAM, 5));
        releaseQuery.countDown();

        for (Future<CellPins> result : results) {
            result.get(5, TimeUnit.SECONDS);
        }
        assertThat(dbQueries).hasValue(2);
    }

    @Test
    @DisplayName("먼저 읽던 요청이 실패하면 기다리던 요청도 같은 예외를 받고, 다음 요청은 다시 읽는다")
    void failureIsSharedThenRetried() throws Exception {
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> {
                    if (dbQueries.incrementAndGet() == 1) {
                        releaseQuery.await(5, TimeUnit.SECONDS);
                        throw new QueryTimeoutException("DB 응답 없음");
                    }
                    return List.of();
                });
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(List.of());

        List<Future<CellPins>> results = loadConcurrently(GANGNAM, CONCURRENT_REQUESTS);
        releaseQuery.countDown();

        for (Future<CellPins> result : results) {
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(QueryTimeoutException.class);
        }
        assertThat(dbQueries).hasValue(1);

        assertThat(loader.load(GANGNAM)).isEqualTo(CellPins.of(List.of()));
        assertThat(dbQueries).hasValue(2);
    }

    @Test
    @DisplayName("먼저 읽던 요청이 Error 로 죽어도 기다리던 요청은 같은 Error 를 받고 끝난다")
    void errorIsSharedWithWaitingRequests() throws Exception {
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> {
                    dbQueries.incrementAndGet();
                    releaseQuery.await(5, TimeUnit.SECONDS);
                    throw new StackOverflowError("DB 조회 중 Error");
                });

        List<Future<CellPins>> results = loadConcurrently(GANGNAM, CONCURRENT_REQUESTS);
        releaseQuery.countDown();

        for (Future<CellPins> result : results) {
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(StackOverflowError.class);
        }
        assertThat(dbQueries).hasValue(1);
    }

    @Test
    @DisplayName("먼저 읽던 요청이 끝난 뒤 들어온 요청은 Redis에 써진 값을 받아 DB에 가지 않는다")
    void lateRequestReadsWrittenCell() {
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> {
                    dbQueries.incrementAndGet();
                    return List.of();
                });
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(List.of());

        loader.load(GANGNAM);
        CellPins late = loader.load(GANGNAM);

        assertThat(late).isEqualTo(CellPins.of(List.of()));
        assertThat(dbQueries).hasValue(1);
        assertThat(meterRegistry.counter(MapPinCellCache.METRIC, "operation", "recheck", "result", "hit").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("읽기가 끝난 칸을 서버에 붙잡아 두지 않아서, Redis에서 지워지면 다시 읽는다")
    void finishedLoadIsNotReused() {
        given(communityPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt()))
                .willAnswer(invocation -> {
                    dbQueries.incrementAndGet();
                    return List.of();
                });
        given(companionPostRepository.findPinsInCell(any(), any(), any(), any(), anyInt())).willReturn(List.of());

        loader.load(GANGNAM);
        cache.evictCells(List.of(GANGNAM));
        loader.load(GANGNAM);

        assertThat(dbQueries).hasValue(2);
    }

    private List<Future<CellPins>> loadConcurrently(GridCell cell, int count) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(count);
        List<Future<CellPins>> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(executor.submit(() -> {
                started.countDown();
                return loader.load(cell);
            }));
        }
        started.await(5, TimeUnit.SECONDS);
        Thread.sleep(200);
        return results;
    }
}
