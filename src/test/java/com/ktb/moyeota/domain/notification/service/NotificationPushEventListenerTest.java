package com.ktb.moyeota.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.notification.event.NotificationPushRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationPushEventListenerTest {

    private final NotificationPushService pushService = mock(NotificationPushService.class);
    private final NotificationPushEventListener listener = new NotificationPushEventListener(pushService);

    @Test
    @DisplayName("이벤트를 받으면 해당 작업을 즉시 발송한다")
    void sendsImmediately() {
        listener.onPushRequested(new NotificationPushRequestedEvent(7L));

        verify(pushService).sendImmediately(7L);
    }

    @Test
    @DisplayName("즉시 발송이 실패해도 예외를 밖으로 던지지 않는다 (스윕이 재시도)")
    void swallowsException() {
        willThrow(new IllegalStateException("db down")).given(pushService).sendImmediately(7L);

        assertThatCode(() -> listener.onPushRequested(new NotificationPushRequestedEvent(7L)))
                .doesNotThrowAnyException();
    }
}
