package com.ktb.moyeota.domain.settlement.message;

public final class ImageAnalysisMessages {

    public static final String REQUEST_TOPIC = "settlement.image-analysis.request";
    public static final String RESULT_TOPIC = "settlement.image-analysis.result";

    public static final String METER_REQUESTED = "meter.analysis.requested";
    public static final String METER_COMPLETED = "meter.analysis.completed";
    public static final int METER_SCHEMA_VERSION = 1;

    public static final String TRANSFER_REQUESTED = "transfer.analysis.requested";
    public static final String TRANSFER_COMPLETED = "transfer.analysis.completed";
    public static final int TRANSFER_SCHEMA_VERSION = 1;

    private static final String KEY_SEPARATOR = ":";

    private ImageAnalysisMessages() {
    }

    public static String key(Long settlementId, Long userId) {
        return settlementId + KEY_SEPARATOR + userId;
    }
}
