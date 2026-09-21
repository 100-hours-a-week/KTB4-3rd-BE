package com.ktb.moyeota.domain.auth.model;

public record CallbackParams(String code, String state, String error, String stateCookie) {
}
