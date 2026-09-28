package com.ktb.moyeota.domain.image.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageSuccessCode implements SuccessCode {

    UPLOAD_URL_ISSUED("이미지 업로드 URL이 발급되었습니다.");

    private final String message;
}
