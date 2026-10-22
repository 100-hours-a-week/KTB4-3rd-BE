package com.ktb.moyeota.domain.taxipot.matching;

public record GroupFit(Anchor origin, Anchor dest) {

    public record Anchor(long requestId, double spreadMeters) {
    }
}
