package com.ktb.moyeota.global.external.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoUserResponse(@JsonProperty("id") Long id) {
}
