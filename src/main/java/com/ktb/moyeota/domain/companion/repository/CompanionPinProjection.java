package com.ktb.moyeota.domain.companion.repository;

import java.math.BigDecimal;

/**
 * 지도 핀(map-pins) 조회용 projection. companions 테이블에서 지도에 필요한 컬럼만 뽑는다.
 * pin 좌표는 origin(출발지) 기준으로 잡는다 — [확인 필요] 명시적으로 정해진 바는 없어서
 * 동행모집 상세조회 응답의 title 조립 방식(출발지 → 도착지)과 맞춰 origin으로 임의 선택함.
 */
public interface CompanionPinProjection {

    Long getId();

    String getOriginName();

    String getDestName();

    BigDecimal getOriginLat();

    BigDecimal getOriginLng();
}
