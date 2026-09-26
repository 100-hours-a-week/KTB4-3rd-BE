package com.ktb.moyeota.domain.report.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    // API 명세서 REPORT 시트 기준: reason=ETC인데 reason_text가 없을 때, 400 VALIDATION_ERROR(field: reason_text)
    REASON_TEXT_REQUIRED(HttpStatus.BAD_REQUEST, "기타 사유를 입력해주세요"),

    // 같은 메시지를 이미 신고한 경우, 409 DUPLICATE_REPORT(field: reported_message_id)
    DUPLICATE_MESSAGE_REPORT(HttpStatus.CONFLICT, "이미 신고한 메시지입니다"),

    // [주의] API 명세서 REPORT 시트에는 없는 케이스 — reported_message_id로 넘어온 메시지가 없거나,
    // 신고자가 그 메시지가 속한 방의 참여자가 아닌 경우. 스펙에 없어 잠정적으로 400으로 처리했다.
    REPORT_TARGET_INVALID(HttpStatus.BAD_REQUEST, "신고 대상을 찾을 수 없습니다"),

    // [주의] 마찬가지로 스펙에 없는 케이스 — reported_user_id가 실제 존재하지 않는 유저인 경우.
    REPORTED_USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 사용자입니다");

    private final HttpStatus status;
    private final String message;
}
