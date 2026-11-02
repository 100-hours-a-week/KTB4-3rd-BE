package com.ktb.moyeota.domain.community.service.cache;

public enum ViewportFallbackReason {

    CACHE_UNAVAILABLE("cache_unavailable"),
    PARTLY_OVERFLOWED("partly_overflowed");

    private final String tagValue;

    ViewportFallbackReason(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
