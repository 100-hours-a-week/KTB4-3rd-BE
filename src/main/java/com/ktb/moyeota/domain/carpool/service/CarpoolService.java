package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.model.CarpoolPin;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.repository.CarpoolPinProjection;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
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

    private final CarpoolRepository carpoolRepository;
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
}
