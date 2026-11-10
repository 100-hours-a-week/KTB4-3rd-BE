package com.ktb.moyeota.domain.taxipot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.config.TaxiPotMatchingProperties;
import com.ktb.moyeota.domain.taxipot.config.TaxiPotMatchingProperties.MatchingMode;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotRegionLease;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRegionLeaseRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegionLeaseServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 8, 0);
    private static final Duration SWEEP_DELAY = Duration.ofSeconds(30);
    private static final Duration LEASE = SWEEP_DELAY.multipliedBy(3);
    private static final RegionCell PANGYO = new RegionCell(16_860, 63_603);
    private static final RegionCell GANGNAM = new RegionCell(16_871, 63_599);

    @Autowired
    private TaxiPotRegionLeaseRepository regionLeaseRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        regionLeaseRepository.deleteAll();
    }

    @Test
    @DisplayName("담당이 없는 칸은 잡고 지금부터 담당 기한까지 내 담당으로 적는다")
    void claimsUnownedRegion() {
        List<RegionCell> claimed = server("A", NOW).claim(List.of(PANGYO));

        assertThat(claimed).containsExactly(PANGYO);
        assertThat(lease(PANGYO)).satisfies(lease -> {
            assertThat(lease.getOwnerId()).startsWith("A-");
            assertThat(lease.getLeaseUntil()).isEqualTo(NOW.plus(LEASE));
        });
    }

    @Test
    @DisplayName("내가 잡은 칸은 다음 주기에 다시 잡으면 기한을 연장한다")
    void renewsOwnRegion() {
        MatchingServerId a = new MatchingServerId("A");
        server(a, NOW).claim(List.of(PANGYO));

        List<RegionCell> claimed = server(a, NOW.plus(SWEEP_DELAY)).claim(List.of(PANGYO));

        assertThat(claimed).containsExactly(PANGYO);
        assertThat(lease(PANGYO).getLeaseUntil()).isEqualTo(NOW.plus(SWEEP_DELAY).plus(LEASE));
    }

    @Test
    @DisplayName("다른 서버가 기한 안에 있는 칸은 잡지 못하고 담당도 바뀌지 않는다")
    void skipsRegionOwnedByOther() {
        server("A", NOW).claim(List.of(PANGYO));

        List<RegionCell> claimed = server("B", NOW.plus(SWEEP_DELAY)).claim(List.of(PANGYO));

        assertThat(claimed).isEmpty();
        assertThat(lease(PANGYO).getOwnerId()).startsWith("A-");
    }

    @Test
    @DisplayName("다른 서버의 담당 기한이 지난 칸은 이어받는다")
    void takesOverExpiredRegion() {
        server("A", NOW).claim(List.of(PANGYO));
        LocalDateTime later = NOW.plus(LEASE).plusSeconds(1);

        List<RegionCell> claimed = server("B", later).claim(List.of(PANGYO));

        assertThat(claimed).containsExactly(PANGYO);
        assertThat(lease(PANGYO)).satisfies(lease -> {
            assertThat(lease.getOwnerId()).startsWith("B-");
            assertThat(lease.getLeaseUntil()).isEqualTo(later.plus(LEASE));
        });
    }

    @Test
    @DisplayName("여러 칸 중 내가 잡을 수 있는 칸만 돌려준다")
    void returnsOnlyClaimedRegions() {
        server("A", NOW).claim(List.of(PANGYO));

        List<RegionCell> claimed = server("B", NOW.plus(SWEEP_DELAY)).claim(List.of(PANGYO, GANGNAM));

        assertThat(claimed).containsExactly(GANGNAM);
    }

    @Test
    @DisplayName("두 서버가 담당 없는 칸을 동시에 잡으면 한 서버만 잡는다")
    void onlyOneServerClaimsConcurrently() {
        RegionLeaseService a = server("A", NOW);
        RegionLeaseService b = server("B", NOW);
        CountDownLatch ready = new CountDownLatch(1);

        CompletableFuture<List<RegionCell>> byA = CompletableFuture.supplyAsync(() -> claimAfter(ready, a));
        CompletableFuture<List<RegionCell>> byB = CompletableFuture.supplyAsync(() -> claimAfter(ready, b));
        ready.countDown();

        assertThat(List.of(byA.join(), byB.join())).containsExactlyInAnyOrder(List.of(PANGYO), List.of());
        assertThat(regionLeaseRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("종료할 때 내 담당만 기한을 지금으로 당겨 다른 서버가 바로 이어받게 한다")
    void releasesOwnRegionsOnShutdown() {
        MatchingServerId a = new MatchingServerId("A");
        server(a, NOW).claim(List.of(PANGYO));
        server("B", NOW).claim(List.of(GANGNAM));
        LocalDateTime shutdownAt = NOW.plusSeconds(10);

        server(a, shutdownAt).releaseAll();

        assertThat(lease(PANGYO).getLeaseUntil()).isEqualTo(shutdownAt);
        assertThat(lease(GANGNAM).getLeaseUntil()).isEqualTo(NOW.plus(LEASE));
        assertThat(server("C", shutdownAt.plusSeconds(1)).claim(List.of(PANGYO))).containsExactly(PANGYO);
    }

    @Test
    @DisplayName("즉시 매칭 모드에서는 종료할 때 담당표를 건드리지 않는다")
    void doesNotReleaseInInstantMode() {
        MatchingServerId a = new MatchingServerId("A");
        server(a, NOW).claim(List.of(PANGYO));

        new RegionLeaseService(regionLeaseRepository, a, properties(MatchingMode.INSTANT), transactionTemplate,
                clockAt(NOW.plusSeconds(10))).releaseAll();

        assertThat(lease(PANGYO).getLeaseUntil()).isEqualTo(NOW.plus(LEASE));
    }

    private List<RegionCell> claimAfter(CountDownLatch ready, RegionLeaseService service) {
        try {
            ready.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return service.claim(List.of(PANGYO));
    }

    private RegionLeaseService server(String hostName, LocalDateTime now) {
        return server(new MatchingServerId(hostName), now);
    }

    private RegionLeaseService server(MatchingServerId serverId, LocalDateTime now) {
        return new RegionLeaseService(regionLeaseRepository, serverId, properties(MatchingMode.BATCH),
                transactionTemplate, clockAt(now));
    }

    private TaxiPotRegionLease lease(RegionCell region) {
        return regionLeaseRepository.findAll().stream()
                .filter(lease -> lease.getRegionRow() == region.row() && lease.getRegionCol() == region.col())
                .findFirst()
                .orElseThrow();
    }

    private static TaxiPotMatchingProperties properties(MatchingMode mode) {
        return new TaxiPotMatchingProperties(mode, 2_000, 100, 8, SWEEP_DELAY, null);
    }

    private static Clock clockAt(LocalDateTime now) {
        return Clock.fixed(now.atZone(KST).toInstant(), KST);
    }
}
