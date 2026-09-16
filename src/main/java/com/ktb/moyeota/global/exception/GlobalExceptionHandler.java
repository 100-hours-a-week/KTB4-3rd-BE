package com.ktb.moyeota.global.exception;

import com.ktb.moyeota.global.common.SnakeCaseConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.RecordComponent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {

        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {

        return validationFailed(toDetails(e.getBindingResult()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException e) {

        List<ValidationDetail> details = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> ValidationDetail.of(
                                fieldNameOf(error, result.getMethodParameter().getParameterName()),
                                ValidationReason.from(lastCodeOf(error.getCodes()), error.getDefaultMessage()))))
                .toList();
        return validationFailed(details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable() {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.MALFORMED_REQUEST));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {

        List<ValidationDetail> details =
                List.of(ValidationDetail.of(e.getName(), ValidationReason.INVALID_FORMAT));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(CommonErrorCode.MALFORMED_REQUEST, details));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {

        return validationFailed(
                List.of(ValidationDetail.of(e.getParameterName(), ValidationReason.REQUIRED)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported() {

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound() {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(CommonErrorCode.ENDPOINT_NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {

        log.error("[INTERNAL_ERROR] {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
    }

    private static ResponseEntity<ErrorResponse> validationFailed(List<ValidationDetail> details) {

        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.of(CommonErrorCode.VALIDATION_ERROR, details));
    }

    private static List<ValidationDetail> toDetails(BindingResult bindingResult) {
        Map<String, Integer> order = declarationOrder(bindingResult.getTarget());
        return bindingResult.getAllErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                .sorted(Comparator
                        .comparingInt((ValidationDetail detail) ->
                                order.getOrDefault(detail.field(), Integer.MAX_VALUE))
                        .thenComparingInt(detail ->
                                ValidationReason.valueOf(detail.reason()).ordinal()))
                .toList();
    }

    private static Map<String, Integer> declarationOrder(Object target) {
        if (target == null || !target.getClass().isRecord()) {
            return Map.of();
        }
        RecordComponent[] components = target.getClass().getRecordComponents();
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < components.length; i++) {
            order.put(SnakeCaseConverter.convert(components[i].getName()), i);
        }
        return order;
    }

    private static ValidationDetail toDetail(ObjectError error) {
        String field = (error instanceof FieldError fieldError)
                ? fieldError.getField()
                : error.getObjectName();
        return ValidationDetail.of(field,
                ValidationReason.from(lastCodeOf(error.getCodes()), error.getDefaultMessage()));
    }

    private static String fieldNameOf(MessageSourceResolvable error, String fallback) {
        return (error instanceof FieldError fieldError) ? fieldError.getField() : fallback;
    }

    private static String lastCodeOf(String[] codes) {
        return (codes == null || codes.length == 0) ? null : codes[codes.length - 1];
    }
}
