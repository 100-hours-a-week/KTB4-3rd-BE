package com.ktb.moyeota.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountNoCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("test-only-moyeota-account-key-!!".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("another-moyeota-account-key-0000".getBytes(StandardCharsets.UTF_8));

    private final AccountNoCipher cipher = new AccountNoCipher(new CryptoProperties(KEY));

    @Test
    @DisplayName("암호화한 계좌번호는 복호화하면 원문으로 돌아온다")
    void roundTrip() {
        assertThat(cipher.decrypt(cipher.encrypt("11012345678"))).isEqualTo("11012345678");
    }

    @Test
    @DisplayName("저장되는 값에 계좌번호 원문이 들어 있지 않다")
    void storedBytesDoNotContainPlainText() {
        byte[] stored = cipher.encrypt("11012345678");

        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("11012345678");
    }

    @Test
    @DisplayName("같은 계좌번호도 암호화할 때마다 다른 값이 된다")
    void sameInputEncryptsDifferently() {
        assertThat(cipher.encrypt("11012345678")).isNotEqualTo(cipher.encrypt("11012345678"));
    }

    @Test
    @DisplayName("저장된 값이 VARBINARY(255)에 들어간다")
    void fitsInColumn() {
        assertThat(cipher.encrypt("12345678901234")).hasSizeLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void wrongKeyFails() {
        byte[] stored = cipher.encrypt("11012345678");
        AccountNoCipher other = new AccountNoCipher(new CryptoProperties(OTHER_KEY));

        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("저장된 값이 한 바이트라도 바뀌면 복호화가 실패한다")
    void tamperedValueFails() {
        byte[] stored = cipher.encrypt("11012345678");
        stored[stored.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }
}
