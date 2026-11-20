package com.ktb.moyeota.domain.notification;

import com.ktb.moyeota.domain.notification.consumer.NotificationPushParallelConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationPushParallelProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "moyeota.notification.push.kafka.push-consumer.type=parallel")
@Import(NotificationPushParallelConsumer.class)
@EnableConfigurationProperties(NotificationPushParallelProperties.class)
class NotificationPushParallelDeliveryIntegrationTest extends NotificationPushDeliveryIntegrationTest {
}
