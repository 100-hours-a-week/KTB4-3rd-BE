package com.ktb.moyeota.global.crypto;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.crypto")
public record CryptoProperties(String accountKey) {

    private static final int KEY_BYTES = 32;

    public CryptoProperties {
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalStateException("moyeota.crypto.account-key가 필요합니다.");
        }
        if (decode(accountKey).length != KEY_BYTES) {
            throw new IllegalStateException(
                    "moyeota.crypto.account-key는 base64로 인코딩한 %d바이트여야 합니다.".formatted(KEY_BYTES));
        }
    }

    public byte[] accountKeyBytes() {
        return decode(accountKey);
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("moyeota.crypto.account-key는 base64 문자열이어야 합니다.", e);
        }
    }
}
