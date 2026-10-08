package com.ktb.moyeota.domain.carpool.model;

import java.util.List;

public record CarpoolPins(List<CarpoolPin> pins, int limit, boolean limitExceeded) {
}
