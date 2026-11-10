package com.ktb.moyeota.domain.taxipot.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    @DisplayName("소수점 6자리 좌표를 마이크로도 정수로 바꾼다")
    void convertsDegreesToMicros() {
        GeoPoint point = GeoPoint.of(new BigDecimal("37.394726"), new BigDecimal("127.111209"));

        assertThat(point).isEqualTo(new GeoPoint(37_394_726L, 127_111_209L));
    }

    @Test
    @DisplayName("자릿수가 달라도 같은 좌표면 같은 점이다")
    void equalsRegardlessOfScale() {
        GeoPoint shorter = GeoPoint.of(new BigDecimal("37.5"), new BigDecimal("127"));
        GeoPoint padded = GeoPoint.of(new BigDecimal("37.500000"), new BigDecimal("127.000000"));

        assertThat(shorter).isEqualTo(padded);
    }

    @Test
    @DisplayName("음수 좌표도 그대로 바꾼다")
    void convertsNegativeDegrees() {
        GeoPoint point = GeoPoint.of(new BigDecimal("-33.868820"), new BigDecimal("-151.209296"));

        assertThat(point).isEqualTo(new GeoPoint(-33_868_820L, -151_209_296L));
    }

    @Test
    @DisplayName("소수점 6자리를 넘는 좌표는 받지 않는다")
    void rejectsMoreThanSixDecimals() {
        assertThatThrownBy(() -> GeoPoint.of(new BigDecimal("37.3947261"), new BigDecimal("127.111209")))
                .isInstanceOf(ArithmeticException.class);
    }
}
