package com.ktb.moyeota.domain.congestion.service;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.config.CongestionCacheConfig;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CongestionCacheReloader {

    private final CongestionLatestCache latestCache;
    private final SpotRepository spotRepository;
    private final TaskExecutor executor;

    public CongestionCacheReloader(CongestionLatestCache latestCache, SpotRepository spotRepository,
                                   @Qualifier(CongestionCacheConfig.RELOAD_EXECUTOR) TaskExecutor executor) {
        this.latestCache = latestCache;
        this.spotRepository = spotRepository;
        this.executor = executor;
    }

    public void requestReload() {
        if (!latestCache.tryStartReload()) {
            return;
        }
        try {
            executor.execute(this::reload);
        } catch (TaskRejectedException e) {
            log.warn("[CONGESTION_CACHE_RELOAD_REJECTED] 이 인스턴스에서 다시 채우기가 이미 돌고 있습니다.");
        }
    }

    private void reload() {
        try {
            List<CachedSpot> spots = spotRepository.findAllLatest().stream()
                    .map(CachedSpot::from)
                    .toList();
            if (!latestCache.reload(spots)) {
                log.warn("[CONGESTION_CACHE_RELOAD_FAILED] 다시 채우기를 끝내지 못했습니다. spots={}", spots.size());
            }
        } catch (RuntimeException e) {
            log.error("[CONGESTION_CACHE_RELOAD_FAILED] 다시 채우기 중 오류가 났습니다.", e);
        }
    }
}
