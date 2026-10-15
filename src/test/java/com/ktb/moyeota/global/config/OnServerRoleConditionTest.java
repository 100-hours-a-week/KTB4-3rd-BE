package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class OnServerRoleConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RestOnly.class, WebSocketOnly.class);

    @Test
    @DisplayName("rest 역할이면 rest 전용 빈만 만든다")
    void restRole() {
        runner.withPropertyValues("SERVICE_NAME=rest").run(context -> {
            assertThat(context).hasBean("restBean");
            assertThat(context).doesNotHaveBean("webSocketBean");
        });
    }

    @Test
    @DisplayName("websocket 역할이면 websocket 전용 빈만 만든다")
    void webSocketRole() {
        runner.withPropertyValues("SERVICE_NAME=websocket").run(context -> {
            assertThat(context).doesNotHaveBean("restBean");
            assertThat(context).hasBean("webSocketBean");
        });
    }

    @Test
    @DisplayName("서비스 이름이 없으면 모든 빈을 만든다")
    void allRoleByDefault() {
        runner.run(context -> {
            assertThat(context).hasBean("restBean");
            assertThat(context).hasBean("webSocketBean");
        });
    }

    @Test
    @DisplayName("알 수 없는 서비스 이름이면 기동에 실패한다")
    void failsOnUnknownRole() {
        runner.withPropertyValues("SERVICE_NAME=websockets")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    static class RestOnly {

        @Bean
        @ConditionalOnServerRole({ServerRole.REST, ServerRole.ALL})
        String restBean() {
            return "rest";
        }
    }

    @Configuration
    static class WebSocketOnly {

        @Bean
        @ConditionalOnServerRole({ServerRole.WEBSOCKET, ServerRole.ALL})
        String webSocketBean() {
            return "websocket";
        }
    }
}
