package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import java.time.LocalDateTime;

public record SentJoinRequest(Long id, Long carpoolId, CompanionRequestStatus status, LocalDateTime createdAt) {
}
