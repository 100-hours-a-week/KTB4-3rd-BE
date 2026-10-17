package com.ktb.moyeota.global.websocket.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.exception.ChatSendFailedException;
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

class RestChatMessageSenderTest {

    private static final String BASE_URL = "http://rest.test";
    private static final String SEND = BASE_URL + "/internal/chat-rooms/6/messages";
    private static final ChatMessageSendRequest REQUEST = new ChatMessageSendRequest("uuid-1", "안녕하세요");

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl(BASE_URL)
            .configureMessageConverters(converters -> converters.withJsonConverter(
                    new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                            .build())));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestChatMessageSender sender = new RestChatMessageSender(builder.build());

    @Test
    @DisplayName("보낸 사람 id와 요청을 rest 내부 API로 넘긴다")
    void postsToRest() {
        server.expect(requestTo(SEND))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"user_id\":4,\"client_message_id\":\"uuid-1\",\"content\":\"안녕하세요\"}"))
                .andRespond(withSuccess("{\"data\":{\"id\":900}}", MediaType.APPLICATION_JSON));

        sender.send(6L, 4L, REQUEST);

        server.verify();
    }

    @Test
    @DisplayName("참여자가 아니라 CHATROOM_NOT_FOUND면 지금처럼 아무것도 보내지 않는다")
    void notParticipant() {
        server.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"존재하지 않는 채팅방입니다.\",\"error\":{\"code\":\"CHATROOM_NOT_FOUND\"}}"));

        assertThatCode(() -> sender.send(6L, 4L, REQUEST)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rest가 오류 코드로 답하면 그 코드와 문구를 client_message_id와 함께 그대로 전한다")
    void passesThroughBusinessError() {
        server.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"동행에 대한 채팅방을 찾을 수 없습니다.\",\"error\":{\"code\":\"COMPANION_CHATROOM_NOT_FOUND\"}}"));

        assertThatThrownBy(() -> sender.send(6L, 4L, REQUEST))
                .isInstanceOfSatisfying(ChatSendFailedException.class, e -> {
                    assertThat(e.getClientMessageId()).isEqualTo("uuid-1");
                    assertThat(e.getMessage()).isEqualTo("동행에 대한 채팅방을 찾을 수 없습니다.");
                    assertThat(e.getError().getCode()).isEqualTo("COMPANION_CHATROOM_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("검증 실패는 field와 details까지 그대로 전한다")
    void passesThroughValidationError() {
        server.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"요청 값 검증에 실패했습니다.\",\"error\":{\"code\":\"VALIDATION_ERROR\",\"field\":\"content\","
                        + "\"details\":[{\"field\":\"content\",\"reason\":\"REQUIRED\"}]}}"));

        assertThatThrownBy(() -> sender.send(6L, 4L, REQUEST))
                .isInstanceOfSatisfying(ChatSendFailedException.class, e -> {
                    assertThat(e.getError().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(e.getError().getField()).isEqualTo("content");
                    assertThat(e.getError().getDetails()).singleElement()
                            .satisfies(detail -> assertThat(detail.getReason()).isEqualTo("REQUIRED"));
                });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = HttpStatus.class, names = {"UNAUTHORIZED", "FORBIDDEN"})
    @DisplayName("내부 키 문제(401·403)는 사용자 인증 오류로 보이지 않게 INTERNAL_ERROR로 알린다")
    void internalAuthFailure(HttpStatus status) {
        server.expect(requestTo(SEND)).andRespond(withStatus(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"인증이 필요합니다.\",\"error\":{\"code\":\"UNAUTHORIZED\"}}"));

        assertInternalError();
    }

    @Test
    @DisplayName("오류 본문을 읽을 수 없으면 INTERNAL_ERROR로 알린다")
    void unreadableBody() {
        server.expect(requestTo(SEND)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertInternalError();
    }

    @Test
    @DisplayName("rest에 닿지 못하면 INTERNAL_ERROR로 알린다")
    void unreachable() {
        server.expect(requestTo(SEND)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertInternalError();
    }

    private void assertInternalError() {
        assertThatThrownBy(() -> sender.send(6L, 4L, REQUEST))
                .isInstanceOfSatisfying(ChatSendFailedException.class, e -> {
                    assertThat(e.getClientMessageId()).isEqualTo("uuid-1");
                    assertThat(e.getError().getCode()).isEqualTo("INTERNAL_ERROR");
                });
    }
}
