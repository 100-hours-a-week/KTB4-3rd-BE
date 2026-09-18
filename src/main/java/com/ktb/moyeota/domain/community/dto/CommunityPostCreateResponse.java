package com.ktb.moyeota.domain.community.dto;

/**
 * [주의할 내용] chat_room_id는 동행모집 등록 API와 동일하게 프론트와 약속된 응답 필드라서
 * 그대로 유지한다. 다만 커뮤니티 게시글 등록 시점에는 Chat 도메인이 아직 개발되지 않아
 * 채팅방을 생성/연결하는 로직이 없다 — 현재는 값을 채우지 못하고, Chat 도메인 개발 후
 * Service 계층에서 채팅방 생성 결과의 id를 채워 넣어야 한다.
 */
public record CommunityPostCreateResponse(
        Long id,
        Long chatRoomId // TODO: Chat 도메인 개발 전까지 값 미할당(null) — 개발 후 채팅방 id로 채울 것
) {
}
