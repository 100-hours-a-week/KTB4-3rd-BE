package com.ktb.moyeota.global.security.jwt;

public record AccessToken(String value, long expiresIn) {
}
