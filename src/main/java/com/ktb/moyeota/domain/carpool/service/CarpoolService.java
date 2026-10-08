package com.ktb.moyeota.domain.carpool.service;

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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarpoolService {

    private static final int PIN_LIMIT = 500;
    private static final Duration PIN_VISIBLE_AFTER_DEPARTURE = Duration.ofMinutes(10);
    private static final int NEARBY_PAGE_SIZE = 10;

    private final CarpoolRepository carpoolRepository;
    private final NearbyCarpoolCursorCodec nearbyCursorCodec;
    private final ImageUrlResolver imageUrlResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CarpoolPins findPins(Viewport viewport) {
        LocalDateTime visibleAfter = LocalDateTime.now(clock).minus(PIN_VISIBLE_AFTER_DEPARTURE);
        List<CarpoolPinProjection> rows = carpoolRepository.findPinsInViewport(
                viewport.swLat(), viewport.swLng(), viewport.neLat(), viewport.neLng(), visibleAfter, PIN_LIMIT + 1);
        if (rows.size() > PIN_LIMIT) {
            return new CarpoolPins(List.of(), PIN_LIMIT, true);
        }
        List<CarpoolPin> pins = rows.stream()
                .map(row -> new CarpoolPin(row.getId(), row.getOriginLat(), row.getOriginLng()))
                .toList();
        return new CarpoolPins(pins, PIN_LIMIT, false);
    }

    @Transactional(readOnly = true)
    public NearbyCarpools findNearby(NearbyCarpoolQuery query) {
        NearbyCarpoolCursor cursor = nearbyCursorCodec.decode(query.cursor()).orElse(null);
        Viewport viewport = query.viewport();
        List<NearbyCarpoolProjection> rows = carpoolRepository.findNearby(
                query.lat(), query.lng(), viewport.swLat(), viewport.swLng(), viewport.neLat(), viewport.neLng(),
                cursor == null ? null : cursor.distance(),
                cursor == null ? null : cursor.id(),
                NEARBY_PAGE_SIZE + 1);

        LocalDateTime now = LocalDateTime.now(clock);
        List<NearbyCarpool> carpools = rows.stream()
                .limit(NEARBY_PAGE_SIZE)
                .map(row -> toNearbyCarpool(row, now))
                .toList();
        String nextCursor = rows.size() > NEARBY_PAGE_SIZE
                ? nearbyCursorCodec.encode(nextCursorOf(carpools.getLast()))
                : null;
        return new NearbyCarpools(carpools, nextCursor);
    }

    private NearbyCarpool toNearbyCarpool(NearbyCarpoolProjection row, LocalDateTime now) {
        return new NearbyCarpool(
                row.getId(),
                row.getHostName(),
                imageUrlResolver.toUrl(row.getHostProfileImageUrl()),
                row.getOriginName(),
                row.getDestName(),
                row.getDepartureAt(),
                row.getDistanceM(),
                row.getCurrentCount(),
                row.getCapacity(),
                row.getCurrentCount() >= row.getCapacity(),
                row.getDepartureAt().isBefore(now));
    }

    private static NearbyCarpoolCursor nextCursorOf(NearbyCarpool last) {
        return new NearbyCarpoolCursor(last.distanceM(), last.id());
    }
}
