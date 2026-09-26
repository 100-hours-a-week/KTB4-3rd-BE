package com.ktb.moyeota.global.external.kakao;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoNaviClient {

    private static final int ROUTE_FOUND = 0;

    private final KakaoProperties kakaoProperties;
    private final KakaoNaviProperties kakaoNaviProperties;
    private final RestClient kakaoRestClient;

    public Optional<Duration> estimateDuration(
            BigDecimal originLat, BigDecimal originLng, BigDecimal destLat, BigDecimal destLng) {
        try {
            KakaoDirectionsResponse response = kakaoRestClient.get()
                    .uri(UriComponentsBuilder.fromUriString(kakaoNaviProperties.directionsUri())
                            .queryParam("origin", originLng.toPlainString() + "," + originLat.toPlainString())
                            .queryParam("destination", destLng.toPlainString() + "," + destLat.toPlainString())
                            .queryParam("summary", true)
                            .build()
                            .toUri())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoProperties.clientId())
                    .retrieve()
                    .body(KakaoDirectionsResponse.class);
            return durationOf(response);
        } catch (RestClientException e) {
            log.warn("[KAKAO_NAVI_FAILED] 길찾기 호출에 실패했습니다.", e);
            return Optional.empty();
        }
    }

    private static Optional<Duration> durationOf(KakaoDirectionsResponse response) {
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.warn("[KAKAO_NAVI_FAILED] 길찾기 응답에 경로가 없습니다.");
            return Optional.empty();
        }
        KakaoDirectionsResponse.Route route = response.routes().getFirst();
        if (route.resultCode() == null || route.resultCode() != ROUTE_FOUND
                || route.summary() == null || route.summary().duration() == null) {
            log.warn("[KAKAO_NAVI_FAILED] 길찾기 결과 코드: {}", route.resultCode());
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(route.summary().duration()));
    }
}
