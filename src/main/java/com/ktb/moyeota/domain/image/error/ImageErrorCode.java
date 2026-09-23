package com.ktb.moyeota.domain.image.error;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements ErrorCode {

    IMAGE_NOT_EXISTS(HttpStatus.UNPROCESSABLE_CONTENT, "업로드된 이미지를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
