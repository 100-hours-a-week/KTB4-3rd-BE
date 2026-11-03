package com.ktb.moyeota.domain.congestion.document;

import com.ktb.moyeota.domain.congestion.model.CauseCode;
import org.springframework.data.mongodb.core.mapping.Field;

public record CauseReport(
        @Field("cause_code") CauseCode causeCode,
        @Field("report_count") int reportCount) {
}
