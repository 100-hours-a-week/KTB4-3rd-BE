package com.ktb.moyeota.domain.report.controller;

import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.dto.ReportCreateResponse;
import com.ktb.moyeota.domain.report.service.ReportService;
import com.ktb.moyeota.domain.report.success.ReportSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        ReportCreateResponse response = reportService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(ReportSuccessCode.REPORT_CREATED, response));
    }
}
