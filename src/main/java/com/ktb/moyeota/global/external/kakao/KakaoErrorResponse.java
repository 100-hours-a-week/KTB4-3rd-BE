package com.ktb.moyeota.global.external.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_code") String errorCode) {
}
