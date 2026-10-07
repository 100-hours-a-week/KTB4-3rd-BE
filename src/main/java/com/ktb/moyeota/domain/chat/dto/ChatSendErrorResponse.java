package com.ktb.moyeota.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.global.common.ErrorResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSendErrorResponse(
        String clientMessageId,
        String message,
        ErrorResponse error
) {
}
