package com.ktb.moyeota.domain.taxipot.dto;

public record CurrentTaxiPotResponse(Long id, Long chatRoomId, String status, int currentCount, int capacity) {
}
