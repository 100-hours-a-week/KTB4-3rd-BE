package com.ktb.moyeota.domain.carpool.entity;

/**
 * companion_requests.status = REJECTED 일 때의 거절 사유. REJECTED 가 아니면 null 이다.
 */
public enum RejectReason {
    /** 호스트가 직접 거절 */
    HOST,
    /** 마지막 자리가 수락되어 정원 마감 */
    CAPACITY_FULL,
    /** 출발 시각이 지나 sweep 이 정리 */
    DEPARTED
}
