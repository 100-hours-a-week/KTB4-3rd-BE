package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;

import com.ktb.moyeota.domain.congestion.batch.CongestionWindowCloseScheduler;
import com.ktb.moyeota.domain.congestion.batch.CongestionWindowCloser;
import com.ktb.moyeota.domain.notification.scheduler.NotificationPushRepublishScheduler;
import com.ktb.moyeota.domain.notification.service.NotificationPushRepublisher;
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
        classes = {SchedulingConfig.class, CongestionWindowCloseScheduler.class,
                NotificationPushRepublishScheduler.class, KafkaJobSchedulerThreadTest.BlockingJob.class},
        properties = {
                "APP_ROLE=worker",
                "moyeota.notification.push.mode=kafka",
                "moyeota.congestion.batch.check-delay=50ms",
                "moyeota.notification.push.sweep-delay=50ms"})
@ImportAutoConfiguration(TaskSchedulingAutoConfiguration.class)
class KafkaJobSchedulerThreadTest {

    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final BlockingQueue<String> CLOSE_THREADS = new LinkedBlockingQueue<>();
    private static final BlockingQueue<String> REPUBLISH_THREADS = new LinkedBlockingQueue<>();

    @MockitoBean
    private CongestionWindowCloser windowCloser;

    @MockitoBean
    private NotificationPushRepublisher republisher;

    @Autowired
    private BlockingJob blockingJob;

    @Test
    @DisplayName("혼잡도 구간 마감 · 알림 재발행은 다른 스케줄 작업이 기본 스레드를 붙잡고 있어도 Kafka 작업 스레드에서 돈다")
    void runsOnKafkaJobThreadWhileDefaultThreadIsBusy() throws Exception {
        willAnswer(invocation -> {
            CLOSE_THREADS.add(Thread.currentThread().getName());
            return null;
        }).given(windowCloser).closeEndedWindows();
        willAnswer(invocation -> {
            REPUBLISH_THREADS.add(Thread.currentThread().getName());
            return 0;
        }).given(republisher).republishDue();

        assertThat(blockingJob.started.await(10, TimeUnit.SECONDS)).isTrue();
        String close = CLOSE_THREADS.poll(10, TimeUnit.SECONDS);
        String republish = REPUBLISH_THREADS.poll(10, TimeUnit.SECONDS);
        RELEASE.countDown();

        assertThat(close).startsWith("kafka-job-");
        assertThat(republish).startsWith("kafka-job-");
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
