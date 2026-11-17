package com.ktb.moyeota.domain.companion.service;

public class EtaUnavailableException extends RuntimeException {

    public EtaUnavailableException(Long companionId) {
        super("길찾기로 도착 예정 시각을 구하지 못했습니다. companionId=" + companionId);
    }
}
