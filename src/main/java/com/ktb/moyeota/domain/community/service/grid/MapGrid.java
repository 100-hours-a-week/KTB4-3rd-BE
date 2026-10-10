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

    public MapGrid(long baseCellMicros, int levelCount, int maxCellsPerAxis) {
        if (baseCellMicros <= 0 || levelCount <= 0 || maxCellsPerAxis <= 0) {
            throw new IllegalArgumentException("격자 설정값은 0보다 커야 합니다");
        }
        this.baseCellMicros = baseCellMicros;
        this.levelCount = levelCount;
        this.maxCellsPerAxis = maxCellsPerAxis;
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
        List<GridCell> cells = new ArrayList<>(levelCount);
        for (int level = 0; level < levelCount; level++) {
            long size = cellMicros(level);
            cells.add(new GridCell(level, Math.floorDiv(latMicros, size), Math.floorDiv(lngMicros, size)));
        }
        return cells;
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

    private long cellMicros(int level) {
        return baseCellMicros << level;
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
