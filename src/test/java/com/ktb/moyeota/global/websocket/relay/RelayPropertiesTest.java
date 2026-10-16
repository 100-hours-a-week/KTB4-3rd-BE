package com.ktb.moyeota.global.websocket.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RelayPropertiesTest {

    @Test
    @DisplayName("지원하는 환경값이면 그대로 쓴다")
    void bindsSupportedEnv() {
        assertThat(bind("dev").env()).isEqualTo("dev");
    }

    @Test
    @DisplayName("지원하지 않는 환경값이면 기동을 막는다")
    void rejectsUnsupportedEnv() {
        assertThatThrownBy(() -> bind("qa"))
                .isInstanceOf(BindException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("환경값이 없으면 기동을 막는다")
    void rejectsMissingEnv() {
        assertThatThrownBy(() -> new RelayProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }

    private RelayProperties bind(String env) {
        return new Binder(new MapConfigurationPropertySource(Map.of("moyeota.relay.env", env)))
                .bind("moyeota.relay", RelayProperties.class)
                .get();
    }
}
