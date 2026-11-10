package com.ktb.moyeota.domain.carpool.model;

import java.util.List;

public record MyCarpoolRequests(RequestDirection direction, List<MyCarpoolRequestItem> items, String nextCursor) {
}
