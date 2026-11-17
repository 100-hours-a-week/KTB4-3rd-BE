package com.ktb.moyeota.global.outbox.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;

import com.ktb.moyeota.global.config.SchedulingConfig;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        classes = {SchedulingConfig.class, OutboxPublishScheduler.class,
                OutboxPublishSchedulerThreadTest.BlockingJob.class},
        properties = {"APP_ROLE=worker", "moyeota.outbox.publish-delay=50ms"})
@ImportAutoConfiguration(TaskSchedulingAutoConfiguration.class)
class OutboxPublishSchedulerThreadTest {

    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final BlockingQueue<String> PUBLISH_THREADS = new LinkedBlockingQueue<>();

    @MockitoBean
    private OutboxPublisher outboxPublisher;

    @Autowired
    private BlockingJob blockingJob;

    @Test
    @DisplayName("아웃박스 발행은 다른 스케줄 작업이 기본 스레드를 붙잡고 있어도 전용 스레드에서 계속 돈다")
    void publishesOnItsOwnThreadWhileDefaultThreadIsBusy() throws Exception {
        willAnswer(invocation -> {
            PUBLISH_THREADS.add(Thread.currentThread().getName());
            return OutboxPublisher.PublishResult.EMPTY;
        }).given(outboxPublisher).publishPending();

        assertThat(blockingJob.started.await(10, TimeUnit.SECONDS)).isTrue();
        String first = PUBLISH_THREADS.poll(10, TimeUnit.SECONDS);
        String second = PUBLISH_THREADS.poll(10, TimeUnit.SECONDS);
        RELEASE.countDown();

        assertThat(first).startsWith("outbox-publish-");
        assertThat(second).startsWith("outbox-publish-");
    }

    static class BlockingJob {

        private final CountDownLatch started = new CountDownLatch(1);

        @Scheduled(fixedDelay = 60_000)
        void holdDefaultThread() throws InterruptedException {
            started.countDown();
            RELEASE.await(30, TimeUnit.SECONDS);
        }
    }
}
