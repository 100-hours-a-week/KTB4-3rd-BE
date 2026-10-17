package com.ktb.moyeota.domain.community.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CellPinsTest {

    @Test
    @DisplayName("핀이 없는 칸은 넘침이 아닌 빈 목록이다")
    void emptyCell() {
        CellPins cellPins = CellPins.of(List.of());

        assertThat(cellPins.overflow()).isFalse();
        assertThat(cellPins.pins()).isEmpty();
    }

    @Test
    @DisplayName("넘친 칸은 핀 목록을 담지 않는다")
    void overflowedCellHasNoPins() {
        CellPins cellPins = CellPins.overflowed();

        assertThat(cellPins.overflow()).isTrue();
        assertThat(cellPins.pins()).isEmpty();
        assertThatThrownBy(() -> new CellPins(true, List.of(pin(1L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("칸 하나에는 핀을 500개까지 담는다")
    void upToMaxPins() {
        assertThat(CellPins.of(pins(CellPins.MAX_PINS)).pins()).hasSize(CellPins.MAX_PINS);
        assertThatThrownBy(() -> CellPins.of(pins(CellPins.MAX_PINS + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 목록은 빈 목록으로 바꾼다")
    void nullPinsBecomeEmpty() {
        assertThat(new CellPins(false, null).pins()).isEmpty();
    }

    private static List<MapPinItem> pins(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(CellPinsTest::pin).toList();
    }

    private static MapPinItem pin(long id) {
        return new MapPinItem(MapPinType.COMMUNITY, id, new BigDecimal("37.497912"), new BigDecimal("127.027600"));
    }
}
