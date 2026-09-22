package com.ktb.moyeota.global.external.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoUserResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    String nickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        String nickname = kakaoAccount.profile().nickname();
        return (nickname == null || nickname.isBlank()) ? null : nickname;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(@JsonProperty("profile") Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(@JsonProperty("nickname") String nickname) {
    }
}
