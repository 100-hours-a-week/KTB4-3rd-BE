package com.ktb.moyeota.domain.community.dto;

import java.math.BigDecimal;

/**
 * 지도 핀 목록의 아이템 하나. COMMUNITY/COMPANION 두 도메인의 데이터가 이 타입 하나로 합쳐진다.
 * community가 정의하는 타입이고, CompanionFeedQueryPort(추후 Service 단계에서 생성)의
 * 반환 타입이기도 하다 — companionpost의 어댑터가 이 타입으로 변환해서 돌려준다.
 */
public record MapPinItem(
        MapPinType type,
        Long id,
        String title,
        BigDecimal lat,
        BigDecimal lng
) {
}
