package com.ktb.moyeota.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.chat.dto.ChatRoomCursor;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ChatRoomCursorCodec {

    private static final String PREFIX = "v1.";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

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
