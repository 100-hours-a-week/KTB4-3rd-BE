package com.ktb.moyeota.domain.chat.exception;

import com.ktb.moyeota.domain.chat.controller.ChatStompController;
import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.ChatSendErrorResponse;
import com.ktb.moyeota.global.common.ErrorResponse;
import com.ktb.moyeota.global.common.FieldErrorDetail;
import com.ktb.moyeota.global.common.SnakeCaseConverter;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.exception.ValidationReason;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice(assignableTypes = ChatStompController.class)
public class ChatStompExceptionHandler {

    private static final String ERROR_DESTINATION = "/sub/errors";

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ChatSendErrorResponse handleValidation(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        List<FieldErrorDetail> details = bindingResult == null
                ? List.of()
                : bindingResult.getAllErrors().stream().map(ChatStompExceptionHandler::toDetail).toList();
        String field = details.isEmpty() ? null : details.getFirst().getField();

        return new ChatSendErrorResponse(
                clientMessageIdOf(bindingResult),
                CommonErrorCode.VALIDATION_ERROR.getMessage(),
                ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR.getCode(), field, details));
    }

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ChatSendErrorResponse handleMalformed(MessageConversionException e) {
        return fail(CommonErrorCode.MALFORMED_REQUEST);
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ChatSendErrorResponse handleBusiness(BusinessException e) {
        return fail(e.getErrorCode());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ChatSendErrorResponse handleUnexpected(Exception e) {
        log.error("[STOMP_INTERNAL_ERROR]", e);
        return fail(CommonErrorCode.INTERNAL_ERROR);
    }

    private static ChatSendErrorResponse fail(ErrorCode errorCode) {
        return new ChatSendErrorResponse(
                null,
                errorCode.getMessage(),
                ErrorResponse.of(errorCode.getCode(), errorCode.getField()));
    }

    private static String clientMessageIdOf(BindingResult bindingResult) {
        if (bindingResult != null && bindingResult.getTarget() instanceof ChatMessageSendRequest request) {
            return request.clientMessageId();
        }
        return null;
    }

    private static FieldErrorDetail toDetail(ObjectError error) {
        String field = (error instanceof FieldError fieldError) ? fieldError.getField() : error.getObjectName();
        String[] codes = error.getCodes();
        String lastCode = (codes == null || codes.length == 0) ? null : codes[codes.length - 1];
        ValidationReason reason = ValidationReason.from(lastCode, error.getDefaultMessage());
        return FieldErrorDetail.of(SnakeCaseConverter.convert(field), reason.name());
    }
}
