package com.ktb.moyeota.domain.congestion.message;

import com.ktb.moyeota.domain.congestion.model.CauseCode;

public record CongestionCause(CauseCode causeCode, int reportCount) {
}
