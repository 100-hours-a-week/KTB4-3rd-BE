package com.ktb.moyeota.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.chat.dto.ChatRoomCursor;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * community/NearbyPostCursorCodec와 같은 방식(v1. 접두사 + base64(JSON)).
 * 다른 점: 잘못된 커서일 때 community는 공통 에러(CommonErrorCode.MALFORMED_REQUEST)를 쓰지만,
 * 채팅 API 명세서는 "code": "INVALID_CURSOR" / "잘못된 커서입니다"를 명시하고 있어
 * 여기서는 Chat 도메인 전용 에러코드(ChatErrorCode.INVALID_CURSOR)를 사용했다.
 */
@Component
public class ChatRoomCursorCodec {

    private static final String PREFIX = "v1.";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); // LocalDateTime 직렬화를 위해 jackson-datatype-jsr310 모듈 등록 필요

    public String encode(ChatRoomCursor cursor) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public ChatRoomCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new ChatRoomCursor(null, null);
        }
        if (!cursor.startsWith(PREFIX)) {
            throw new BusinessException(ChatErrorCode.INVALID_CURSOR);
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.substring(PREFIX.length()));
            return objectMapper.readValue(json, ChatRoomCursor.class);
        } catch (Exception e) {
            throw new BusinessException(ChatErrorCode.INVALID_CURSOR);
        }
    }
}
