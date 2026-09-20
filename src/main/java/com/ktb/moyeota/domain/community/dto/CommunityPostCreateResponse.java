package com.ktb.moyeota.domain.community.dto;


public record CommunityPostCreateResponse(
        Long id,
        Long chatRoomId // TODO: Chat 도메인 개발 전까지 값 미할당(null) — 개발 후 채팅방 id로 채울 것
) {
}
