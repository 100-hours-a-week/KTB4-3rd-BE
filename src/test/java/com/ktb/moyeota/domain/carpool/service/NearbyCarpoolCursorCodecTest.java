package com.ktb.moyeota.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.model.NearbyCarpoolCursor;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NearbyCarpoolCursorCodecTest {

    private final NearbyCarpoolCursorCodec codec = new NearbyCarpoolCursorCodec();

    @Test
    @DisplayName("만든 커서를 풀면 같은 거리와 id가 나온다")
    void roundTrip() {
        NearbyCarpoolCursor cursor = new NearbyCarpoolCursor(320.4567891234, 51L);

        assertThat(codec.decode(codec.encode(cursor))).contains(cursor);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("커서가 없으면 첫 페이지다")
    void emptyCursor(String cursor) {
        assertThat(codec.decode(cursor)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "v2.eyJkaXN0YW5jZSI6MSwiaWQiOjF9", "v1.!!!", "v1.bm90LWpzb24"})
    @DisplayName("형식이 틀린 커서는 INVALID_CURSOR다")
    void malformedCursor(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CURSOR);
    }

    @Test
    @DisplayName("거리나 id가 빠진 커서는 INVALID_CURSOR다")
    void missingField() {
        String cursor = "v1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"distance\":320.5}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CURSOR);
    }
}
