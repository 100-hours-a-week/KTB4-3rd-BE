package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotLeftEvent;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchCanceledEvent;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchRequestedEvent;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.message.MatchCanceledData;
import com.ktb.moyeota.domain.taxipot.message.MatchLeftData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchRideStartedData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.taxi-pot.matching", name = "mode", havingValue = "kafka")
public class MatchingOutboxListener {

    private final TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;
    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onRequested(TaxiPotMatchRequestedEvent event) {
        TaxiPotMatchRequest request = find(event.requestId());
        record(keyOf(request), MatchingMessages.REQUESTED, MatchRequestData.from(toMatchRequest(request)));
    }

    @EventListener
    public void onCanceled(TaxiPotMatchCanceledEvent event) {
        TaxiPotMatchRequest request = find(event.requestId());
        record(keyOf(request), MatchingMessages.CANCELED, new MatchCanceledData(request.getId()));
    }

    @EventListener
    public void onLeft(TaxiPotLeftEvent event) {
        taxiPotMatchRequestRepository.findFirstByCompanionIdAndUserIdAndStatusOrderByIdDesc(
                        event.taxiPotId(), event.userId(), TaxiPotMatchRequestStatus.MATCHED)
                .ifPresent(request ->
                        record(keyOf(request), MatchingMessages.LEFT, new MatchLeftData(request.getId())));
    }

    @EventListener
    public void onRideStarted(TaxiPotRideStartedEvent event) {
        Map<String, List<Long>> requestIdsByKey = taxiPotMatchRequestRepository
                .findAllByCompanionIdAndStatusOrderById(event.taxiPotId(), TaxiPotMatchRequestStatus.MATCHED)
                .stream()
                .collect(Collectors.groupingBy(MatchingOutboxListener::keyOf, LinkedHashMap::new,
                        Collectors.mapping(TaxiPotMatchRequest::getId, Collectors.toList())));
        requestIdsByKey.forEach((key, requestIds) ->
                record(key, MatchingMessages.RIDE_STARTED, new MatchRideStartedData(requestIds)));
    }

    private void record(String key, String eventType, Object data) {
        outboxRecorder.record(MatchingMessages.REQUEST_TOPIC, key, eventType, MatchingMessages.SCHEMA_VERSION, data);
    }

    private TaxiPotMatchRequest find(Long requestId) {
        return taxiPotMatchRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalStateException("매칭 요청이 없다: " + requestId));
    }

    private static String keyOf(TaxiPotMatchRequest request) {
        return MatchingMessages.key(new RegionCell(request.getRegionRow(), request.getRegionCol()));
    }

    private static MatchRequest toMatchRequest(TaxiPotMatchRequest request) {
        return new MatchRequest(
                request.getId(),
                request.getUser().getId(),
                new Place(request.getOriginName(), GeoPoint.of(request.getOriginLat(), request.getOriginLng())),
                new Place(request.getDestName(), GeoPoint.of(request.getDestLat(), request.getDestLng())),
                request.getDepartureAt(),
                request.getCreatedAt());
    }
}
