package com.ktb.moyeota.domain.report.dto;

import java.time.LocalDateTime;

public record ReportCreateResponse(
        Long id,
        LocalDateTime createdAt
) {
}
