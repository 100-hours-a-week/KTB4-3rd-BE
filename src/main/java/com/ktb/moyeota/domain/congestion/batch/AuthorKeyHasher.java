package com.ktb.moyeota.domain.congestion.batch;

import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class AuthorKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;

    public AuthorKeyHasher(@Value("${moyeota.congestion.author-key-secret}") String secret) {
        byte[] bytes = decode(secret);
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "moyeota.congestion.author-key-secret는 base64로 인코딩한 %d바이트여야 합니다.".formatted(KEY_BYTES));
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    public String hash(String authorId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(authorId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("작성자 가명 키를 만들지 못했습니다.", e);
        }
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("moyeota.congestion.author-key-secret는 base64 문자열이어야 합니다.", e);
        }
    }
}
