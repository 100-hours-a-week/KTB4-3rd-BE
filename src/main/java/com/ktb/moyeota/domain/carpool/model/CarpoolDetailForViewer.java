package com.ktb.moyeota.domain.carpool.model;

import java.util.Optional;

public record CarpoolDetailForViewer(CarpoolDetail detail, Optional<MyCarpoolRequest> myRequest) {
}
