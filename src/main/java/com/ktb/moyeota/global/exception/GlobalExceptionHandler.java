package com.ktb.moyeota.global.exception;

import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.common.ErrorResponse;
import com.ktb.moyeota.global.common.FieldErrorDetail;
import com.ktb.moyeota.global.common.SnakeCaseConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.RecordComponent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
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
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {

        return fail(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {

        return validationFailed(toDetails(e.getBindingResult()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException e) {

        List<FieldErrorDetail> details = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> detailOf(
                                fieldNameOf(error, result.getMethodParameter().getParameterName()),
                                ValidationReason.from(lastCodeOf(error.getCodes()), error.getDefaultMessage()))))
                .toList();
        return validationFailed(details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable() {

        return fail(CommonErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {

        FieldErrorDetail detail = detailOf(e.getName(), ValidationReason.INVALID_FORMAT);
        return fail(CommonErrorCode.MALFORMED_REQUEST, List.of(detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {

        return validationFailed(
                List.of(detailOf(e.getParameterName(), ValidationReason.REQUIRED)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported() {

        return fail(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound() {

        return fail(CommonErrorCode.ENDPOINT_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception e, HttpServletRequest request) {

        log.error("[INTERNAL_ERROR] {} {}", request.getMethod(), request.getRequestURI(), e);
        return fail(CommonErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<ApiResponse<Void>> fail(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage(), ErrorResponse.of(errorCode.name())));
    }

    private static ResponseEntity<ApiResponse<Void>> fail(
            ErrorCode errorCode, List<FieldErrorDetail> details) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getMessage(),
                        ErrorResponse.of(errorCode.name(), representativeFieldOf(details), details)));
    }

    private static ResponseEntity<ApiResponse<Void>> validationFailed(
            List<FieldErrorDetail> details) {
        return fail(CommonErrorCode.VALIDATION_ERROR, details);
    }

    private static String representativeFieldOf(List<FieldErrorDetail> details) {
        return details.isEmpty() ? null : details.getFirst().getField();
    }

    private static List<FieldErrorDetail> toDetails(BindingResult bindingResult) {
        Map<String, Integer> order = declarationOrder(bindingResult.getTarget());
        return bindingResult.getAllErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                .sorted(Comparator
                        .comparingInt((FieldErrorDetail detail) ->
                                order.getOrDefault(detail.getField(), Integer.MAX_VALUE))
                        .thenComparingInt(detail ->
                                ValidationReason.valueOf(detail.getReason()).ordinal()))
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

    private static FieldErrorDetail toDetail(ObjectError error) {
        String field = (error instanceof FieldError fieldError)
                ? fieldError.getField()
                : error.getObjectName();
        return detailOf(field,
                ValidationReason.from(lastCodeOf(error.getCodes()), error.getDefaultMessage()));
    }

    private static FieldErrorDetail detailOf(String field, ValidationReason reason) {
        return FieldErrorDetail.of(SnakeCaseConverter.convert(field), reason.name());
    }

    private static String fieldNameOf(MessageSourceResolvable error, String fallback) {
        return (error instanceof FieldError fieldError) ? fieldError.getField() : fallback;
    }

    private static String lastCodeOf(String[] codes) {
        return (codes == null || codes.length == 0) ? null : codes[codes.length - 1];
    }
}
