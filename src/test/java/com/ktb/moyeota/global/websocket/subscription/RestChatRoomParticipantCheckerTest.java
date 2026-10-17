package com.ktb.moyeota.global.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class RestChatRoomParticipantCheckerTest {

    private static final String BASE_URL = "http://rest.test";
    private static final String CHECK = BASE_URL + "/internal/chat-rooms/6/participants/4";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl(BASE_URL)
            .configureMessageConverters(converters -> converters.withJsonConverter(
                    new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                            .build())));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestChatRoomParticipantChecker checker = new RestChatRoomParticipantChecker(builder.build());

    @Test
    @DisplayName("rest가 참여자라고 답하면 true")
    void participant() {
        server.expect(requestTo(CHECK))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"message\":\"채팅방 참여 여부를 확인했습니다\",\"data\":{\"is_participant\":true}}",
                        MediaType.APPLICATION_JSON));

        assertThat(checker.isParticipant(6L, 4L)).isTrue();
    }

    @Test
    @DisplayName("rest가 참여자가 아니라고 답하면 false")
    void notParticipant() {
        server.expect(requestTo(CHECK))
                .andRespond(withSuccess("{\"data\":{\"is_participant\":false}}", MediaType.APPLICATION_JSON));

        assertThat(checker.isParticipant(6L, 4L)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = HttpStatus.class, names = {"UNAUTHORIZED", "INTERNAL_SERVER_ERROR", "SERVICE_UNAVAILABLE"})
    @DisplayName("rest가 실패로 답하면 구독을 막도록 false")
    void failedResponse(HttpStatus status) {
        server.expect(requestTo(CHECK)).andRespond(withStatus(status));

        assertThat(checker.isParticipant(6L, 4L)).isFalse();
    }

    @Test
    @DisplayName("rest에 닿지 못하면 구독을 막도록 false")
    void unreachable() {
        server.expect(requestTo(CHECK)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(checker.isParticipant(6L, 4L)).isFalse();
    }
}
