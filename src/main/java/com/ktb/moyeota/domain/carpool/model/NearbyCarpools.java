package com.ktb.moyeota.domain.carpool.model;

import java.util.List;

public record NearbyCarpools(List<NearbyCarpool> carpools, String nextCursor) {
}
