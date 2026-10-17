package com.ktb.moyeota.domain.community.service.cache;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import java.util.List;

public record CellPins(boolean overflow, List<MapPinItem> pins) {

    public static final int MAX_PINS = 500;

    public CellPins {
        pins = pins == null ? List.of() : List.copyOf(pins);
        if (overflow && !pins.isEmpty()) {
            throw new IllegalArgumentException("넘친 칸은 핀 목록을 담지 않습니다.");
        }
        if (pins.size() > MAX_PINS) {
            throw new IllegalArgumentException("칸 하나에 담을 수 있는 핀은 " + MAX_PINS + "개까지입니다.");
        }
    }

    public static CellPins of(List<MapPinItem> pins) {
        return new CellPins(false, pins);
    }

    public static CellPins overflowed() {
        return new CellPins(true, List.of());
    }
}
