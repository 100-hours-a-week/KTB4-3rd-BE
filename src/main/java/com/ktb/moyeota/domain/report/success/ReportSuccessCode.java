package com.ktb.moyeota.domain.report.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportSuccessCode implements SuccessCode {

    REPORT_CREATED("신고가 접수되었습니다");

    private final String message;
}
