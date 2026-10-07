package com.ktb.moyeota.domain.chat.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.controller.ChatStompController;
import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.ChatSendErrorResponse;
import com.ktb.moyeota.global.exception.BusinessException;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

class ChatStompExceptionHandlerTest {

    private ChatStompExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChatStompExceptionHandler();
    }

    @Test
    @DisplayName("검증 실패 시 VALIDATION_ERROR와 실패 필드, client_message_id를 돌려준다")
    void validationError() throws Exception {
        ChatMessageSendRequest request = new ChatMessageSendRequest("uuid-1", "a".repeat(501));
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "chatMessageSendRequest");
        bindingResult.addError(new FieldError("chatMessageSendRequest", "content", request.content(), false,
                new String[]{"Size.chatMessageSendRequest.content", "Size.content", "Size"}, null,
                "크기가 0에서 500 사이여야 합니다"));

        ChatSendErrorResponse response = handler.handleValidation(
                new MethodArgumentNotValidException(MessageBuilder.withPayload("{}").build(), sendPayloadParameter(),
                        bindingResult));

        assertThat(response.clientMessageId()).isEqualTo("uuid-1");
        assertThat(response.error().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.error().getField()).isEqualTo("content");
        assertThat(response.error().getDetails()).singleElement().satisfies(detail -> {
            assertThat(detail.getField()).isEqualTo("content");
            assertThat(detail.getReason()).isEqualTo("LENGTH_OUT_OF_RANGE");
        });
    }

    @Test
    @DisplayName("필드명은 snake_case로 내려간다")
    void validationErrorFieldIsSnakeCase() throws Exception {
        ChatMessageSendRequest request = new ChatMessageSendRequest(null, "안녕하세요");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "chatMessageSendRequest");
        bindingResult.addError(new FieldError("chatMessageSendRequest", "clientMessageId", null, false,
                new String[]{"NotBlank.clientMessageId", "NotBlank"}, null, "공백일 수 없습니다"));

        ChatSendErrorResponse response = handler.handleValidation(
                new MethodArgumentNotValidException(MessageBuilder.withPayload("{}").build(), sendPayloadParameter(),
                        bindingResult));

        assertThat(response.clientMessageId()).isNull();
        assertThat(response.error().getField()).isEqualTo("client_message_id");
        assertThat(response.error().getDetails().getFirst().getReason()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("JSON 형식이 잘못되면 MALFORMED_REQUEST")
    void malformed() {
        ChatSendErrorResponse response = handler.handleMalformed(new MessageConversionException("bad json"));

        assertThat(response.error().getCode()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("비즈니스 예외는 해당 에러 코드를 그대로 돌려준다")
    void business() {
        ChatSendErrorResponse response = handler.handleBusiness(
                new BusinessException(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND));

        assertThat(response.error().getCode()).isEqualTo("COMPANION_CHATROOM_NOT_FOUND");
        assertThat(response.message()).isEqualTo(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("예상하지 못한 예외는 INTERNAL_ERROR")
    void unexpected() {
        ChatSendErrorResponse response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.error().getCode()).isEqualTo("INTERNAL_ERROR");
    }

    private static MethodParameter sendPayloadParameter() throws NoSuchMethodException {
        return new MethodParameter(ChatStompController.class.getMethod(
                "send", Long.class, ChatMessageSendRequest.class, Principal.class), 1);
    }
}
