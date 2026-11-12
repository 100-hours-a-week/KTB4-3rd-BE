package com.ktb.moyeota.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.dto.MessageCursor;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageSeqCursorCodecTest {

    private final MessageSeqCursorCodec codec = new MessageSeqCursorCodec();

    @Test
    @DisplayName("번호를 v2 커서로 만들고 다시 같은 번호로 읽는다")
    void roundTrips() {
        String cursor = codec.encode(128L);

        assertThat(cursor).startsWith("v2.");
        assertThat(codec.decode(cursor)).isEqualTo(128L);
    }

    @Test
    @DisplayName("옛 v1 커서는 INVALID_CURSOR 로 거절한다")
    void rejectsV1() {
        String v1 = new MessageCursorCodec().encode(new MessageCursor(10L));

        assertInvalid(v1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "v2.", "v2.!!!", "v3.eyJzZXEiOjF9", "garbage"})
    @DisplayName("형식이 틀린 커서는 INVALID_CURSOR 로 거절한다")
    void rejectsMalformed(String cursor) {
        assertInvalid(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"seq\":0}", "{\"seq\":-3}", "{\"seq\":\"x\"}"})
    @DisplayName("번호가 없거나 1보다 작으면 INVALID_CURSOR 로 거절한다")
    void rejectsMissingOrNonPositiveSeq(String json) {
        assertInvalid("v2." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("null 커서는 INVALID_CURSOR 로 거절한다")
    void rejectsNull() {
        assertInvalid(null);
    }

    private void assertInvalid(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_CURSOR));
    }
}
