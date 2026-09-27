package com.ktb.moyeota.domain.report.dto;

import com.ktb.moyeota.domain.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull
        Long reportedUserId,

        Long reportedMessageId,

        Long companionId,

        @NotNull
        ReportReason reason,

        @Size(max = 500)
        String reasonText
) {
}
