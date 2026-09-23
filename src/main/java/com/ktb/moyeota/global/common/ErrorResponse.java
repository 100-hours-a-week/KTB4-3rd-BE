package com.ktb.moyeota.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;


@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String code;
    private final String field;
    private final List<FieldErrorDetail> details;

    private ErrorResponse(String code, String field, List<FieldErrorDetail> details) {
        this.code = code;
        this.field = field;
        this.details = details;
    }

    public static ErrorResponse of(String code) {
        return new ErrorResponse(code, null, null);
    }

    public static ErrorResponse of(String code, String field) {
        return new ErrorResponse(code, field, null);
    }

    public static ErrorResponse of(String code, String field, List<FieldErrorDetail> details) {
        return new ErrorResponse(code, field, details);
    }
}
