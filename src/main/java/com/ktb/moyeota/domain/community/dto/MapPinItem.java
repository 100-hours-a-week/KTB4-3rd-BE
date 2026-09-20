package com.ktb.moyeota.domain.community.dto;

import java.math.BigDecimal;

/**
 * 지도 핀 목록의 아이템 하나. COMMUNITY/COMPANION 두 도메인의 데이터가 이 타입 하나로 합쳐진다.
 * community가 정의하는 타입이다. CommunityMapPinService가 CommunityPostRepository와
 * companionpost 도메인의 CompanionPostRepository를 각각 직접 호출해 이 타입으로 조립한다
 * (Port/Adapter는 도입하지 않기로 함 — 서비스가 커지면 재검토, TODO로 남김).
 */
public record MapPinItem(
        MapPinType type,
        Long id,
        String title,
        BigDecimal lat,
        BigDecimal lng
) {
}
