package com.ktb.moyeota.domain.taxipot.dto;

public record CurrentTaxiPotResponse(Long id, Long chatRoomId, String status, Integer currentCount, int capacity) {
}
