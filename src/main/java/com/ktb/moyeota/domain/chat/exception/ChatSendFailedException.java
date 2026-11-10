package com.ktb.moyeota.domain.chat.exception;

import com.ktb.moyeota.global.common.ErrorResponse;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import lombok.Getter;

@Getter
public class ChatSendFailedException extends RuntimeException {

    private final String clientMessageId;
    private final ErrorResponse error;

    public ChatSendFailedException(String clientMessageId) {
        this(clientMessageId, CommonErrorCode.INTERNAL_ERROR.getMessage(),
                ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR.getCode()));
    }

    public ChatSendFailedException(String clientMessageId, String message, ErrorResponse error) {
        super(message);
        this.clientMessageId = clientMessageId;
        this.error = error;
    }
}
