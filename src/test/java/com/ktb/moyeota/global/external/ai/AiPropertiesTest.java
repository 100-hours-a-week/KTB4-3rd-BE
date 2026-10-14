package com.ktb.moyeota.global.external.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiPropertiesTest {

    @Test
    @DisplayName("주소만 주면 연결 2초 · 읽기 30초로 정해진다")
    void defaultTimeouts() {
        AiProperties properties = bind(Map.of("moyeota.ai.base-url", "http://ai.test"));

        assertThat(properties).isEqualTo(
                new AiProperties("http://ai.test", Duration.ofSeconds(2), Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("주소가 없으면 기동 시점에 거부한다")
    void rejectsMissingBaseUrl() {
        assertThatThrownBy(() -> bind(Map.of("moyeota.ai.read-timeout", "30s")))
                .isInstanceOf(BindException.class)
                .rootCause()
                .hasMessageContaining("moyeota.ai.base-url");
    }

    private static AiProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("moyeota.ai", AiProperties.class);
    }
}
