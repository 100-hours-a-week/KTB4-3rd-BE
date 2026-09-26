package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CorsPropertiesTest {

    @Test
    @DisplayName("추가 오리진이 비어 있으면 프론트 오리진 하나만 허용한다")
    void frontOnlyWhenNoExtra() {
        assertThat(bind("http://dev.moyeota.com,").allowedOrigins())
                .containsExactly("http://dev.moyeota.com");
    }

    @Test
    @DisplayName("추가 오리진을 프론트 오리진 뒤에 이어 허용한다")
    void appendsExtraOrigins() {
        assertThat(bind("http://dev.moyeota.com,http://localhost:3000,http://localhost:5173").allowedOrigins())
                .containsExactly("http://dev.moyeota.com", "http://localhost:3000", "http://localhost:5173");
    }

    @Test
    @DisplayName("빈 항목만 남으면 기동을 막는다")
    void rejectsWhenOnlyBlank() {
        assertThatThrownBy(() -> bind(","))
                .isInstanceOf(BindException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private CorsProperties bind(String allowedOrigins) {
        return new Binder(new MapConfigurationPropertySource(
                Map.of("moyeota.cors.allowed-origins", allowedOrigins)))
                .bind("moyeota.cors", CorsProperties.class)
                .get();
    }
}
