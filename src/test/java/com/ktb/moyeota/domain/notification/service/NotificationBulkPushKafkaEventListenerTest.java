package com.ktb.moyeota.domain.notification.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.notification.event.NotificationBulkPushRequestedEvent;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationBulkPushKafkaEventListenerTest {

    private final NotificationPushPublisher publisher = mock(NotificationPushPublisher.class);
    private final NotificationBulkPushKafkaEventListener listener =
            new NotificationBulkPushKafkaEventListener(publisher);

    @Test
    @DisplayName("전체 발송 요청 이벤트를 받으면 작업 id들을 전체 발송 토픽으로 발행한다")
    void publishesToBulkTopic() {
        listener.onBulkPushRequested(new NotificationBulkPushRequestedEvent(List.of(3L, 4L)));

        verify(publisher).publish(NotificationPushChannel.BULK, List.of(3L, 4L));
    }
}
