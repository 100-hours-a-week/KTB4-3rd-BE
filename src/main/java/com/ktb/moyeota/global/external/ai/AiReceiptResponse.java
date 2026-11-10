package com.ktb.moyeota.global.external.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

record AiReceiptResponse(Boolean success, @JsonProperty("amount_cost") Integer amountCost)
        implements AiSuccessResponse {
}
