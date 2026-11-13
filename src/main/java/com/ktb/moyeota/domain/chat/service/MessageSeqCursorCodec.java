package com.ktb.moyeota.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.chat.dto.MessageSeqCursor;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * MongoDB 메시지의 방별 번호(seq)를 담는 v2 커서. 옛 v1(MySQL id) 커서는 접두사가 달라 INVALID_CURSOR 로 거절한다.
 */
@Component
public class MessageSeqCursorCodec {

    private static final String PREFIX = "v2.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String encode(long seq) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(new MessageSeqCursor(seq));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public long decode(String cursor) {
        if (cursor == null || !cursor.startsWith(PREFIX)) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        MessageSeqCursor decoded;
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.substring(PREFIX.length()));
            decoded = objectMapper.readValue(json, MessageSeqCursor.class);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        if (decoded.seq() == null || decoded.seq() < 1) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        return decoded.seq();
    }
}
