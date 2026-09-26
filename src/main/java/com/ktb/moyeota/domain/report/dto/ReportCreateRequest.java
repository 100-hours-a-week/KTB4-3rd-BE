package com.ktb.moyeota.domain.report.dto;

import com.ktb.moyeota.domain.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull
        Long reportedUserId,

        // 이번 단계는 메시지 신고만 지원 — 유저 단독 신고(null)는 범위 제외.
        @NotNull
        Long reportedMessageId,

        @NotNull
        ReportReason reason,

        // 선택. reason=ETC일 때만 필수 — 교차 필드 검증이라 @Valid로 불가, 서비스 레이어에서 처리
        @Size(max = 500)
        String reasonText
) {
}
