package com.ktb.moyeota.domain.user.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.user.dto.PushTokenRequest;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushTokenServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0, 123_456_789);
    private static final Long USER_ID = 42L;

    private final PushTokenRepository repository = mock(PushTokenRepository.class);
    private final PushTokenService service = new PushTokenService(
            repository, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @Test
    @DisplayName("등록하면 토큰을 UPSERT한다 (시각은 마이크로초까지)")
    void register() {
        service.register(USER_ID, new PushTokenRequest("fcm-token"));

        verify(repository).upsert(USER_ID, "fcm-token", LocalDateTime.of(2026, 10, 12, 9, 0, 0, 123_456_000));
    }

    @Test
    @DisplayName("삭제하면 본인 토큰 삭제를 요청한다")
    void delete() {
        service.delete(USER_ID, new PushTokenRequest("fcm-token"));

        verify(repository).deleteByUserIdAndToken(USER_ID, "fcm-token");
    }
}
