package com.ktb.moyeota.domain.community.dto;

public record NearbyPostCursor(
        TableCursor community,
        TableCursor companion
) {

    public record TableCursor(Double distance, Long id) {
    }
}
