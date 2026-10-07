package com.ktb.moyeota.global.external.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoUnlinkClient {

    private static final String TARGET_ID_TYPE = "user_id";
    private static final String ADMIN_KEY_SCHEME = "KakaoAK ";

    private final KakaoUnlinkProperties kakaoUnlinkProperties;
    private final RestClient kakaoRestClient;

    public void unlink(String kakaoUserId) {
        if (!kakaoUnlinkProperties.hasAdminKey()) {
            log.warn("[KAKAO_UNLINK_SKIPPED] admin-key가 없어 연결 끊기를 건너뜁니다. kakaoUserId={}", kakaoUserId);
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", TARGET_ID_TYPE);
        form.add("target_id", kakaoUserId);

        try {
            kakaoRestClient.post()
                    .uri(kakaoUnlinkProperties.unlinkUri())
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_KEY_SCHEME + kakaoUnlinkProperties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("[KAKAO_UNLINK_FAILED] kakaoUserId={}", kakaoUserId, e);
        }
    }
}
