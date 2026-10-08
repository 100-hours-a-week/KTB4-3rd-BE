package com.ktb.moyeota.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ViewportTest {

    @Test
    @DisplayName("가로·세로가 1도인 뷰포트는 허용한다")
    void allowsOneDegreeSpan() {
        Viewport viewport = viewport("37.0", "127.0", "38.0", "128.0");

        assertThat(viewport.neLat()).isEqualByComparingTo("38.0");
    }

    @ParameterizedTest
    @CsvSource({
            "37.5, 127.0, 37.5, 127.1",
            "37.6, 127.0, 37.5, 127.1",
            "37.5, 127.1, 37.6, 127.1",
            "37.5, 127.2, 37.6, 127.1"
    })
    @DisplayName("남서 좌표가 북동 좌표보다 작지 않으면 VIEWPORT_OUT_OF_RANGE다")
    void rejectsInvertedViewport(String swLat, String swLng, String neLat, String neLng) {
        assertThatThrownBy(() -> viewport(swLat, swLng, neLat, neLng))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VIEWPORT_OUT_OF_RANGE);
    }

    @ParameterizedTest
    @CsvSource({
            "37.0, 127.0, 38.01, 127.5",
            "37.0, 127.0, 37.5, 128.01"
    })
    @DisplayName("가로나 세로가 1도를 넘으면 VIEWPORT_TOO_LARGE다")
    void rejectsTooLargeViewport(String swLat, String swLng, String neLat, String neLng) {
        assertThatThrownBy(() -> viewport(swLat, swLng, neLat, neLng))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.VIEWPORT_TOO_LARGE);
    }

    private static Viewport viewport(String swLat, String swLng, String neLat, String neLng) {
        return new Viewport(new BigDecimal(swLat), new BigDecimal(swLng), new BigDecimal(neLat), new BigDecimal(neLng));
    }
}
