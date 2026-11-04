package com.ktb.moyeota.domain.congestion.batch;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.message.CongestionAnalysisRequestData;
import com.ktb.moyeota.domain.congestion.message.CongestionMessages;
import com.ktb.moyeota.domain.congestion.message.PostBundle;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class KafkaCongestionAnalysisRequester implements CongestionAnalysisRequester {

    private final PostBundleReader bundleReader;
    private final SpotRepository spotRepository;
    private final CongestionLatestCache latestCache;
    private final EventEnvelopeFactory envelopeFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void request(CongestionWindow window, List<Long> spotIds) {
        Map<Long, CompletableFuture<SendResult<String, String>>> sends = new LinkedHashMap<>();
        for (Long spotId : spotIds) {
            List<PostBundle> bundles = bundleReader.read(spotId, window);
            if (!bundles.isEmpty()) {
                sends.put(spotId, send(new CongestionAnalysisRequestData(spotId, window.end(), bundles)));
            }
        }

        Instant windowEnd = window.end().toInstant();
        List<Long> analyzingSpotIds = new ArrayList<>();
        sends.forEach((spotId, send) -> {
            try {
                send.join();
                spotRepository.markAnalyzing(spotId, windowEnd);
                analyzingSpotIds.add(spotId);
            } catch (CompletionException e) {
                log.error("[CONGESTION_ANALYSIS_REQUEST_FAILED] spot_id={} window_end={}",
                        spotId, window.end(), e.getCause());
            }
        });
        if (!analyzingSpotIds.isEmpty()) {
            latestCache.putAll(spotRepository.findAllById(analyzingSpotIds).stream().map(CachedSpot::from).toList());
        }
    }

    private CompletableFuture<SendResult<String, String>> send(CongestionAnalysisRequestData data) {
        String value = jsonMapper.writeValueAsString(envelopeFactory.create(
                CongestionMessages.ANALYSIS_REQUESTED, CongestionMessages.SCHEMA_VERSION, data));
        return kafkaTemplate.send(CongestionMessages.REQUEST_TOPIC, String.valueOf(data.spotId()), value);
    }
}
