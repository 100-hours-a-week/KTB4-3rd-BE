package com.ktb.moyeota.domain.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.community.dto.NearbyPostCursor;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class NearbyPostCursorCodec {

    private static final String PREFIX = "v1.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String encode(NearbyPostCursor cursor) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public NearbyPostCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new NearbyPostCursor(null, null);
        }
        if (!cursor.startsWith(PREFIX)) {
            throw new BusinessException(CommonErrorCode.MALFORMED_REQUEST);
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.substring(PREFIX.length()));
            return objectMapper.readValue(json, NearbyPostCursor.class);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.MALFORMED_REQUEST);
        }
    }
}
