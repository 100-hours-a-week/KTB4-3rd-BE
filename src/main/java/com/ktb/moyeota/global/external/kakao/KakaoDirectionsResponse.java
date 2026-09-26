package com.ktb.moyeota.global.external.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoDirectionsResponse(@JsonProperty("routes") List<Route> routes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(@JsonProperty("result_code") Integer resultCode, @JsonProperty("summary") Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(@JsonProperty("duration") Long duration) {
    }
}
