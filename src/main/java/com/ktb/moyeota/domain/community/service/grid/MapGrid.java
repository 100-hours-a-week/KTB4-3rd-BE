package com.ktb.moyeota.domain.community.service.grid;

import com.ktb.moyeota.global.common.Viewport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MapGrid {

    private static final int MICRO_SCALE = 6;

    private final long baseCellMicros;
    private final int levelCount;
    private final int maxCellsPerAxis;
    private final int splitLevelCount;

    public MapGrid(long baseCellMicros, int levelCount, int maxCellsPerAxis, int splitLevelCount) {
        if (baseCellMicros <= 0 || levelCount <= 0 || maxCellsPerAxis <= 0) {
            throw new IllegalArgumentException("격자 설정값은 0보다 커야 합니다");
        }
        if (splitLevelCount < 0 || splitLevelCount >= Long.SIZE
                || baseCellMicros % (1L << splitLevelCount) != 0) {
            throw new IllegalArgumentException("가장 작은 칸도 백만분의 1도 단위로 나눠떨어져야 합니다");
        }
        this.baseCellMicros = baseCellMicros;
        this.levelCount = levelCount;
        this.maxCellsPerAxis = maxCellsPerAxis;
        this.splitLevelCount = splitLevelCount;
    }

    public int levelFor(Viewport viewport) {
        BigDecimal span = viewport.neLat().subtract(viewport.swLat())
                .max(viewport.neLng().subtract(viewport.swLng()));
        for (int level = 0; level < levelCount - 1; level++) {
            BigDecimal coverable = BigDecimal.valueOf(cellMicros(level) * maxCellsPerAxis, MICRO_SCALE);
            if (span.compareTo(coverable) <= 0) {
                return level;
            }
        }
        return levelCount - 1;
    }

    public List<GridCell> cellsCovering(Viewport viewport) {
        int level = levelFor(viewport);
        long size = cellMicros(level);
        long minRow = Math.floorDiv(floorMicros(viewport.swLat()), size);
        long maxRow = Math.floorDiv(floorMicros(viewport.neLat()), size);
        long minCol = Math.floorDiv(floorMicros(viewport.swLng()), size);
        long maxCol = Math.floorDiv(floorMicros(viewport.neLng()), size);

        List<GridCell> cells = new ArrayList<>();
        for (long row = minRow; row <= maxRow; row++) {
            for (long col = minCol; col <= maxCol; col++) {
                cells.add(new GridCell(level, row, col));
            }
        }
        return cells;
    }

    public List<GridCell> cellsContaining(BigDecimal lat, BigDecimal lng) {
        long latMicros = floorMicros(lat);
        long lngMicros = floorMicros(lng);
        List<GridCell> cells = new ArrayList<>(splitLevelCount + levelCount);
        for (int level = -splitLevelCount; level < levelCount; level++) {
            long size = cellMicros(level);
            cells.add(new GridCell(level, Math.floorDiv(latMicros, size), Math.floorDiv(lngMicros, size)));
        }
        return cells;
    }

    public int lowestSplitLevel(int level) {
        return Math.max(-splitLevelCount, level - splitLevelCount);
    }

    public List<GridCell> childrenOverlapping(GridCell cell, Viewport viewport) {
        if (cell.level() <= -splitLevelCount) {
            throw new IllegalArgumentException("가장 작은 칸은 더 나눌 수 없습니다: " + cell);
        }
        List<GridCell> children = new ArrayList<>(4);
        for (long row = cell.row() * 2; row <= cell.row() * 2 + 1; row++) {
            for (long col = cell.col() * 2; col <= cell.col() * 2 + 1; col++) {
                GridCell child = new GridCell(cell.level() - 1, row, col);
                if (overlaps(child, viewport)) {
                    children.add(child);
                }
            }
        }
        return children;
    }

    public CellBounds boundsOf(GridCell cell) {
        long size = cellMicros(cell.level());
        return new CellBounds(
                degrees(cell.row() * size),
                degrees(cell.col() * size),
                degrees((cell.row() + 1) * size),
                degrees((cell.col() + 1) * size));
    }

    public boolean isFullyInside(GridCell cell, Viewport viewport) {
        long size = cellMicros(cell.level());
        return ceilMicros(viewport.swLat()) <= cell.row() * size
                && (cell.row() + 1) * size <= floorMicros(viewport.neLat())
                && ceilMicros(viewport.swLng()) <= cell.col() * size
                && (cell.col() + 1) * size <= floorMicros(viewport.neLng());
    }

    private boolean overlaps(GridCell cell, Viewport viewport) {
        long size = cellMicros(cell.level());
        return cell.row() * size <= floorMicros(viewport.neLat())
                && ceilMicros(viewport.swLat()) < (cell.row() + 1) * size
                && cell.col() * size <= floorMicros(viewport.neLng())
                && ceilMicros(viewport.swLng()) < (cell.col() + 1) * size;
    }

    private long cellMicros(int level) {
        return level >= 0 ? baseCellMicros << level : baseCellMicros >> -level;
    }

    private static long floorMicros(BigDecimal degrees) {
        return degrees.setScale(MICRO_SCALE, RoundingMode.FLOOR).unscaledValue().longValueExact();
    }

    private static long ceilMicros(BigDecimal degrees) {
        return degrees.setScale(MICRO_SCALE, RoundingMode.CEILING).unscaledValue().longValueExact();
    }

    private static BigDecimal degrees(long micros) {
        return BigDecimal.valueOf(micros, MICRO_SCALE);
    }
}
