package com.ktb.moyeota.domain.community.service.grid;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapGridConfig {

    private static final long BASE_CELL_MICROS = 10_000;
    private static final int LEVEL_COUNT = 6;
    private static final int MAX_CELLS_PER_AXIS = 4;

    @Bean
    public MapGrid mapGrid() {
        return new MapGrid(BASE_CELL_MICROS, LEVEL_COUNT, MAX_CELLS_PER_AXIS);
    }
}
