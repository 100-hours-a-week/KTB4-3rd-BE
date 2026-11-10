package com.ktb.moyeota.global.metrics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ECS 태스크 ID를 task metadata v4({@code ECS_CONTAINER_METADATA_URI_V4}/task)에서 읽어 {@code ECS_TASK_ID} 속성으로 넣는다.
 * 이미 {@code ECS_TASK_ID}가 있거나 metadata 주소가 없으면 아무것도 하지 않는다.
 * 조회에 실패하면 속성을 넣지 않으므로 application-prod.yaml의 HOSTNAME fallback이 쓰인다.
 * 설정 값이 준비된 직후 실행되므로 로그는 남기지 않는다.
 */
public class EcsTaskIdEnvironmentListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final String TASK_ID_KEY = "ECS_TASK_ID";
    static final String METADATA_URI_KEY = "ECS_CONTAINER_METADATA_URI_V4";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (StringUtils.hasText(environment.getProperty(TASK_ID_KEY))) {
            return;
        }
        String metadataUri = environment.getProperty(METADATA_URI_KEY);
        if (!StringUtils.hasText(metadataUri)) {
            return;
        }
        taskIdFrom(metadataUri).ifPresent(taskId -> environment.getPropertySources()
                .addFirst(new MapPropertySource("ecs-task-id", Map.of(TASK_ID_KEY, taskId))));
    }

    static Optional<String> taskIdFrom(String metadataUri) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(metadataUri + "/task"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode taskArn = JsonMapper.builder().build().readTree(response.body()).path("TaskARN");
            // TaskARN은 arn:aws:ecs:<region>:<account>:task/<cluster>/<task-id> 형식이다.
            String arn = taskArn.asString("");
            int slash = arn.lastIndexOf('/');
            if (slash < 0 || slash == arn.length() - 1) {
                return Optional.empty();
            }
            return Optional.of(arn.substring(slash + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
