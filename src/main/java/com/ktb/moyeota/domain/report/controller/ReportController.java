package com.ktb.moyeota.domain.report.controller;

import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.dto.ReportCreateResponse;
import com.ktb.moyeota.domain.report.entity.Report;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.report.service.ReportService;
import com.ktb.moyeota.domain.report.success.ReportSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.common.ErrorResponse;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<ReportCreateResponse>> create(
            @AuthUser Long userId, @Valid @RequestBody ReportCreateRequest request) {
        Report report = reportService.create(userId, request);
        ReportCreateResponse response = new ReportCreateResponse(report.getId(), report.getCreatedAt());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(ReportSuccessCode.REPORT_CREATED, response));
    }

    // API 명세서(REPORT 시트)의 에러 응답은 code/field가 공통 BusinessException/ErrorCode 구조로는
    // 표현이 안 돼서(공통 코드는 이번 작업 범위 밖이라 손 안 댐), 이 컨트롤러 안에서만
    // 스펙에 맞는 code/field로 다시 매핑해서 응답한다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        String code = errorCode.name();
        String field = null;

        if (errorCode == ReportErrorCode.REASON_TEXT_REQUIRED) {
            code = "VALIDATION_ERROR";
            field = "reason_text";
        } else if (errorCode == ReportErrorCode.DUPLICATE_MESSAGE_REPORT) {
            code = "DUPLICATE_REPORT";
            field = "reported_message_id";
        }

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage(), ErrorResponse.of(code, field)));
    }
}
