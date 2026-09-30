package com.ktb.moyeota.domain.chat.dto;

import java.util.List;

/**
 * 채팅 메시지 목록 조회 응답. before_cursor, after_cursor는 항상 함께 내려간다.
 * - 값: 그 방향으로 더 가져올 메시지가 있다
 * - null: 그 방향으로 더 가져올 메시지가 없다
 * 요청한 방향의 커서는 새로 계산하고, 반대 방향 커서는 요청으로 받은 값을 그대로 돌려준다.
 */
public record MessageListResponse(
        List<MessageItem> items,
        String beforeCursor,
        String afterCursor
) {
}
