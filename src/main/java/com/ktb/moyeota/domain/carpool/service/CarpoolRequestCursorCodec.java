package com.ktb.moyeota.domain.carpool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.carpool.model.CarpoolRequestCursor;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CarpoolRequestCursorCodec {

    private static final String PREFIX = "v1.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String encode(CarpoolRequestCursor cursor) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public Optional<CarpoolRequestCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        if (!cursor.startsWith(PREFIX)) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        CarpoolRequestCursor decoded = parse(cursor.substring(PREFIX.length()));
        if (decoded.id() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        return Optional.of(decoded);
    }

    private CarpoolRequestCursor parse(String encoded) {
        try {
            return objectMapper.readValue(Base64.getUrlDecoder().decode(encoded), CarpoolRequestCursor.class);
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
    }
}
