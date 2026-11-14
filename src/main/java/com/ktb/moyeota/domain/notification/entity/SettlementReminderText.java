package com.ktb.moyeota.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementReminderText {

    HOST("운행 종료를 안했습니다. 운행을 종료해주세요. 운행 종료를 해야 다른 택시팟 매칭에 참여가 가능합니다."),

    RIDER("방장에게 운행 종료 요청을 해주세요. 운행 종료를 해야 다른 택시팟 매칭에 참여가 가능합니다.");

    private final String text;

    public static SettlementReminderText of(boolean host) {
        return host ? HOST : RIDER;
    }
}
