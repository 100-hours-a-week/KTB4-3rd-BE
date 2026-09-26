package com.ktb.moyeota.domain.report.dto;

import com.ktb.moyeota.domain.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;

public record ReportCreateRequest(
        @NotNull
        Long reportedUserId,

        @NotNull
        Long reportedMessageId,

        @NotNull
        ReportReason reason,

        String reasonText
) {
}
