package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OAuthPropertiesTest {

    private static final String CALLBACK = "http://dev.moyeota.com/auth/callback";

    @Test
    @DisplayName("추가 오리진을 쓰지 않으면 빈 목록이다")
    void absentMeansEmpty() {
        assertThat(bind(Map.of()).extraFrontOrigins()).isEmpty();
    }

    @Test
    @DisplayName("환경변수의 빈 값도 빈 목록이다")
    void blankMeansEmpty() {
        assertThat(bind(Map.of("moyeota.oauth.extra-front-origins", "")).extraFrontOrigins()).isEmpty();
    }

    @Test
    @DisplayName("쉼표로 여러 오리진을 넣을 수 있다")
    void commaSeparated() {
        assertThat(bind(Map.of(
                "moyeota.oauth.extra-front-origins", "http://localhost:3000,http://localhost:5173"))
                .extraFrontOrigins())
                .containsExactly("http://localhost:3000", "http://localhost:5173");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000/", "http://localhost:3000/auth", "localhost:3000"})
    @DisplayName("경로가 붙었거나 오리진 형태가 아니면 기동을 막는다")
    void rejectsNonOrigin(String origin) {
        assertThatThrownBy(() -> new OAuthProperties(CALLBACK, List.of(origin)))
                .isInstanceOf(IllegalStateException.class);
    }

    private OAuthProperties bind(Map<String, String> extra) {
        Map<String, String> source = new HashMap<>(extra);
        source.put("moyeota.oauth.front-callback-uri", CALLBACK);
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("moyeota.oauth", OAuthProperties.class)
                .get();
    }
}
