package com.ktb.moyeota.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public record MeterImageResponse(String meterAnalysisStatus, @JsonInclude(NON_NULL) String imageUrl) {
}
