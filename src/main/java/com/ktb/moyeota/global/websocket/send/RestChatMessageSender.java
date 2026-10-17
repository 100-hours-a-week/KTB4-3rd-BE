package com.ktb.moyeota.global.websocket.send;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.InternalChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.exception.ChatSendFailedException;
import com.ktb.moyeota.global.common.ErrorResponse;
import com.ktb.moyeota.global.common.FieldErrorDetail;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@ConditionalOnServerRole(ServerRole.WEBSOCKET)
@RequiredArgsConstructor
public class RestChatMessageSender implements ChatMessageSender {

    private static final String PATH = "/internal/chat-rooms/{room_id}/messages";

    private final RestClient internalRestClient;

    @Override
    public void send(Long roomId, Long userId, ChatMessageSendRequest request) {
        try {
            internalRestClient.post()
                    .uri(PATH, roomId)
                    .body(new InternalChatMessageSendRequest(userId, request.clientMessageId(), request.content()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            ErrorBody body = errorBodyOf(e);
            if (body != null && ChatErrorCode.CHATROOM_NOT_FOUND.getCode().equals(body.error().code())) {
                return;
            }
            log.error("[CHAT_SEND_FAILED] 메시지 저장 응답이 실패했습니다. roomId={} userId={} status={} code={}",
                    roomId, userId, e.getStatusCode().value(), body == null ? null : body.error().code());
            if (body == null || isInternalAuthFailure(e)) {
                throw new ChatSendFailedException(request.clientMessageId());
            }
            throw new ChatSendFailedException(request.clientMessageId(), body.message(), body.error().toErrorResponse());
        } catch (RestClientException e) {
            log.warn("[CHAT_SEND_FAILED] rest 서버에 메시지 저장을 요청하지 못했습니다. roomId={} userId={}",
                    roomId, userId, e);
            throw new ChatSendFailedException(request.clientMessageId());
        }
    }

    private static boolean isInternalAuthFailure(RestClientResponseException e) {
        return e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                || e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN);
    }

    private static ErrorBody errorBodyOf(RestClientResponseException e) {
        try {
            ErrorBody body = e.getResponseBodyAs(ErrorBody.class);
            return body == null || body.error() == null || body.error().code() == null ? null : body;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    record ErrorBody(String message, ErrorContent error) {
    }

    record ErrorContent(String code, String field, List<DetailContent> details) {

        ErrorResponse toErrorResponse() {
            List<FieldErrorDetail> fieldDetails = details == null ? null
                    : details.stream().map(detail -> FieldErrorDetail.of(detail.field(), detail.reason())).toList();
            return ErrorResponse.of(code, field, fieldDetails);
        }
    }

    record DetailContent(String field, String reason) {
    }
}
