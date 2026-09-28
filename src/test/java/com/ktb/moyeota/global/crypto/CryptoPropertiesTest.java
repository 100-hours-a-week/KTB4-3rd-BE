package com.ktb.moyeota.global.crypto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CryptoPropertiesTest {

    @Test
    @DisplayName("base64로 인코딩한 32바이트 키면 통과한다")
    void acceptsAes256Key() {
        assertThatCode(() -> new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("키가 없으면 기동 시점에 거부한다")
    void rejectsMissingKey() {
        assertThatThrownBy(() -> new CryptoProperties(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("account-key");
    }

    @Test
    @DisplayName("32바이트가 아닌 키는 거부한다")
    void rejectsWrongLength() {
        assertThatThrownBy(() -> new CryptoProperties(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    @Test
    @DisplayName("base64가 아닌 값은 거부한다")
    void rejectsNonBase64() {
        assertThatThrownBy(() -> new CryptoProperties("not base64 !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
