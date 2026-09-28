package com.ktb.moyeota.global.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.oauth")
public record OAuthProperties(String frontCallbackUri, List<String> extraFrontOrigins) {

    public OAuthProperties {
        if (frontCallbackUri == null || frontCallbackUri.isBlank()) {
            throw new IllegalStateException("moyeota.oauth.front-callback-uri가 필요합니다.");
        }
        extraFrontOrigins = extraFrontOrigins == null
                ? List.of()
                : extraFrontOrigins.stream().filter(origin -> !origin.isBlank()).toList();
        extraFrontOrigins.forEach(OAuthProperties::requireOrigin);
    }

    private static void requireOrigin(String origin) {
        URI uri = URI.create(origin);
        boolean onlyOrigin = uri.getHost() != null
                && uri.getRawPath().isEmpty()
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        if (!onlyOrigin) {
            throw new IllegalStateException(
                    "moyeota.oauth.extra-front-origins는 경로 없는 오리진이어야 합니다: " + origin);
        }
    }
}
