package com.ktb.moyeota.domain.companion.message;

import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.global.event.InvalidEventException;

public record CompanionChangedData(Long companionId, CompanionKind kind, Long userId) {

    public CompanionChangedData {
        if (companionId == null || kind == null) {
            throw new InvalidEventException("동행 바뀜 메시지에 companion_id · kind 가 있어야 합니다");
        }
    }
}
