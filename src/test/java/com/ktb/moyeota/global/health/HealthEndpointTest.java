package com.ktb.moyeota.global.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("readiness 헬스체크는 토큰 없이 200 UP을 준다")
    void readinessWithoutToken() {
        ResponseEntity<String> response = get("/api/actuator/health/readiness");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("liveness 헬스체크도 토큰 없이 200 UP을 준다")
    void livenessWithoutToken() {
        ResponseEntity<String> response = get("/api/actuator/health/liveness");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    private ResponseEntity<String> get(String path) {
        return RestClient.create("http://localhost:" + port)
                .get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> {
                })
                .toEntity(String.class);
    }
}
