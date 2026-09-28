package com.ktb.moyeota.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AccountNoCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountNoCipher(CryptoProperties cryptoProperties) {
        this.key = new SecretKeySpec(cryptoProperties.accountKeyBytes(), "AES");
    }

    public byte[] encrypt(String accountNo) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        byte[] encrypted = run(Cipher.ENCRYPT_MODE, iv, accountNo.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
    }

    public String decrypt(byte[] stored) {
        ByteBuffer buffer = ByteBuffer.wrap(stored);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        return new String(run(Cipher.DECRYPT_MODE, iv, encrypted), StandardCharsets.UTF_8);
    }

    private byte[] run(int mode, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("계좌번호 암·복호화에 실패했습니다.", e);
        }
    }
}
