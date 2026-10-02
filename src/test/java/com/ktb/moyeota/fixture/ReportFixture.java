package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.user.entity.User;

public final class ReportFixture {

    private ReportFixture() {
    }

    public static ReportCreateRequest messageReport(User target, Message message) {
        return new ReportCreateRequest(target.getId(), message.getId(), null, ReportReason.ABUSE, null);
    }

    public static ReportCreateRequest userReport(User target, Companion companion) {
        return new ReportCreateRequest(target.getId(), null, companion.getId(), ReportReason.NO_SHOW, null);
    }
}
