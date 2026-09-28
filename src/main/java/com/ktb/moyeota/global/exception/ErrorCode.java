package com.ktb.moyeota.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String name();

    HttpStatus getStatus();

    String getMessage();

    default String getCode() {
        return name();
    }

    default String getField() {
        return null;
    }
}
