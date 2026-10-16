package com.ktb.moyeota.domain.community.service.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.global.common.Viewport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MapGridTest {

    private final MapGrid grid = new MapGrid(10_000, 6, 4);

    @ParameterizedTest(name = "카카오 레벨 {0} 화면은 격자 단계 {5}", quoteTextArguments = false)
    @CsvSource({
            "1, 37.556848327083, 126.97015279749114, 37.55860554744368, 126.97126407172286, 0",
            "3, 37.555766988000926, 126.96959584762644, 37.55928142886534, 126.9718183900138, 0",
            "5, 37.54062794504187, 126.96180023803333, 37.56874348770015, 126.97957989753655, 0",
            "6, 37.5233255062858, 126.95289482636633, 37.579556626312346, 126.98845259622964, 1",
            "7, 37.48871847245554, 126.9350963243424, 37.601180843197824, 127.00620569447494, 2",
            "8, 37.41949581472321, 126.89954846639634, 37.64442101379723, 127.04174274386762, 3",
            "9, 37.28101641970275, 126.82864822621896, 37.730868127469215, 127.11294065073236, 4",
            "10, 37.0039235434236, 126.68762084922345, 37.90362804094284, 127.25583488054873, 5"
    })
    @DisplayName("프론트가 보내는 화면마다 한 변에 칸 4개 이하가 되는 가장 작은 단계를 고른다")
    void picksSmallestLevelForFrontViewports(
            int kakaoLevel, String swLat, String swLng, String neLat, String neLng, int expectedLevel) {
        Viewport viewport = viewport(swLat, swLng, neLat, neLng);

        assertThat(grid.levelFor(viewport)).isEqualTo(expectedLevel);
        assertThat(grid.cellsCovering(viewport)).hasSizeLessThanOrEqualTo(25);
    }

    @ParameterizedTest(name = "위도 {0}~{2}, 경도 {1}~{3} → 단계 {4}", quoteTextArguments = false)
    @CsvSource({
            "37.50, 127.00, 37.54, 127.01, 0",
            "37.50, 127.00, 37.540001, 127.01, 1",
            "37.50, 127.00, 37.51, 127.040001, 1"
    })
    @DisplayName("폭이 칸 4개를 정확히 채우면 그 단계, 조금이라도 넘으면 다음 단계다")
    void levelBoundary(String swLat, String swLng, String neLat, String neLng, int expectedLevel) {
        assertThat(grid.levelFor(viewport(swLat, swLng, neLat, neLng))).isEqualTo(expectedLevel);
    }

    @Test
    @DisplayName("지도를 조금 밀어 좌표가 달라져도 걸친 칸은 같다")
    void sameCellsAfterSmallDrag() {
        List<GridCell> before = grid.cellsCovering(viewport("37.495", "127.025", "37.505", "127.035"));
        List<GridCell> after = grid.cellsCovering(viewport("37.495", "127.028", "37.505", "127.038"));

        assertThat(after).containsExactlyElementsOf(before);
        assertThat(before).containsExactly(
                new GridCell(0, 3749, 12702), new GridCell(0, 3749, 12703),
                new GridCell(0, 3750, 12702), new GridCell(0, 3750, 12703));
    }

    @Test
    @DisplayName("소수점 아래가 길어도 내림으로 칸을 정한다")
    void longDecimals() {
        List<GridCell> cells = grid.cellsContaining(new BigDecimal("37.5421416243628"), new BigDecimal("126.96179946576201"));

        assertThat(cells.get(0)).isEqualTo(new GridCell(0, 3754, 12696));
    }

    @Test
    @DisplayName("음수 좌표는 0 쪽이 아니라 더 작은 쪽으로 내린다")
    void negativeCoordinates() {
        List<GridCell> cells = grid.cellsContaining(new BigDecimal("-33.868800"), new BigDecimal("-0.000001"));

        assertThat(cells.get(0)).isEqualTo(new GridCell(0, -3387, -1));
        assertThat(grid.boundsOf(cells.get(0)).contains(new BigDecimal("-33.868800"), new BigDecimal("-0.000001")))
                .isTrue();
    }

    @Test
    @DisplayName("한 좌표는 단계마다 하나씩, 그 좌표를 담는 칸을 갖는다")
    void cellsContainingEveryLevel() {
        BigDecimal lat = new BigDecimal("37.497942");
        BigDecimal lng = new BigDecimal("127.027621");

        List<GridCell> cells = grid.cellsContaining(lat, lng);

        assertThat(cells).extracting(GridCell::level).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(cells).allSatisfy(cell -> assertThat(grid.boundsOf(cell).contains(lat, lng)).isTrue());
    }

    @Test
    @DisplayName("칸 경계 위의 점은 아래·왼쪽 경계를 가진 칸 하나에만 들어간다")
    void boundaryPointBelongsToOneCell() {
        BigDecimal lat = new BigDecimal("37.50");
        BigDecimal lng = new BigDecimal("127.03");

        GridCell cell = grid.cellsContaining(lat, lng).get(0);

        assertThat(cell).isEqualTo(new GridCell(0, 3750, 12703));
        assertThat(grid.boundsOf(new GridCell(0, 3749, 12703)).contains(lat, lng)).isFalse();
        assertThat(grid.boundsOf(new GridCell(0, 3750, 12702)).contains(lat, lng)).isFalse();
    }

    @Test
    @DisplayName("칸 범위는 칸 크기만큼의 백만분의 1도 단위 좌표다")
    void bounds() {
        CellBounds bounds = grid.boundsOf(new GridCell(2, 937, 3175));

        assertThat(bounds.minLat()).isEqualByComparingTo("37.48");
        assertThat(bounds.maxLat()).isEqualByComparingTo("37.52");
        assertThat(bounds.minLng()).isEqualByComparingTo("127.00");
        assertThat(bounds.maxLng()).isEqualByComparingTo("127.04");
    }

    @Test
    @DisplayName("칸 전체가 화면 안에 있을 때만 완전히 들어 있다고 본다")
    void fullyInside() {
        Viewport viewport = viewport("37.495", "127.025", "37.535", "127.045");

        assertThat(grid.isFullyInside(new GridCell(0, 3750, 12703), viewport)).isTrue();
        assertThat(grid.isFullyInside(new GridCell(0, 3749, 12702), viewport)).isFalse();
        assertThat(grid.isFullyInside(new GridCell(0, 3753, 12703), viewport)).isFalse();
    }

    @Test
    @DisplayName("화면 경계가 칸 경계와 겹치면 완전히 들어 있다")
    void fullyInsideOnExactEdges() {
        Viewport viewport = viewport("37.50", "127.03", "37.51", "127.04");

        assertThat(grid.isFullyInside(new GridCell(0, 3750, 12703), viewport)).isTrue();
    }

    @Test
    @DisplayName("화면 경계가 칸 경계보다 아주 조금 안쪽이면 일부만 걸친 것으로 본다")
    void partialWhenEdgeSlightlyInside() {
        Viewport viewport = viewport("37.5000000001", "127.03", "37.51", "127.04");

        assertThat(grid.isFullyInside(new GridCell(0, 3750, 12703), viewport)).isFalse();
    }

    @ParameterizedTest(quoteTextArguments = false)
    @CsvSource({"0, 6, 4", "10000, 0, 4", "10000, 6, 0"})
    @DisplayName("설정값이 0 이하이면 만들 수 없다")
    void rejectsNonPositiveSettings(long baseCellMicros, int levelCount, int maxCellsPerAxis) {
        assertThatThrownBy(() -> new MapGrid(baseCellMicros, levelCount, maxCellsPerAxis))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Viewport viewport(String swLat, String swLng, String neLat, String neLng) {
        return new Viewport(new BigDecimal(swLat), new BigDecimal(swLng), new BigDecimal(neLat), new BigDecimal(neLng));
    }
}
