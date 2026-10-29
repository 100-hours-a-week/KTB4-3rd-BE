package com.ktb.moyeota.global.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class OutboxEventRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 18, 0);

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("발행 대기 이벤트만 id 순서로 정한 개수만큼 가져온다")
    void findsPendingInIdOrder() {
        Long first = persist(pending()).getId();
        OutboxEvent published = pending();
        published.markPublished(NOW);
        persist(published);
        Long second = persist(pending()).getId();
        persist(pending());

        List<OutboxEvent> found = outboxEventRepository.findPending(2);

        assertThat(found).extracting(OutboxEvent::getId).containsExactly(first, second);
    }

    @Test
    @DisplayName("발행 완료는 발행 대기 이벤트에만 한 번에 반영되고 이미 발행된 이벤트는 그대로 둔다")
    void marksPublishedOnlyPending() {
        Long pending = persist(pending()).getId();
        OutboxEvent alreadyPublished = pending();
        alreadyPublished.markPublished(NOW.minusMinutes(1));
        Long published = persist(alreadyPublished).getId();

        int updated = outboxEventRepository.markPublished(List.of(pending, published), NOW);

        assertThat(updated).isEqualTo(1);
        OutboxEvent marked = outboxEventRepository.findById(pending).orElseThrow();
        assertThat(marked.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(marked.getPublishedAt()).isEqualTo(NOW);
        assertThat(outboxEventRepository.findById(published).orElseThrow().getPublishedAt())
                .isEqualTo(NOW.minusMinutes(1));
    }

    @Test
    @DisplayName("발행 실패는 시도 횟수를 올리고 오류를 남기며 발행 대기로 둔다")
    void recordsFailure() {
        Long first = persist(pending()).getId();
        Long second = persist(pending()).getId();

        outboxEventRepository.recordFailure(List.of(first, second), "ack 대기 시간 초과");
        outboxEventRepository.recordFailure(List.of(first), "브로커 연결 실패");

        OutboxEvent failedTwice = outboxEventRepository.findById(first).orElseThrow();
        assertThat(failedTwice.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(failedTwice.getAttempts()).isEqualTo(2);
        assertThat(failedTwice.getLastError()).isEqualTo("브로커 연결 실패");
        OutboxEvent failedOnce = outboxEventRepository.findById(second).orElseThrow();
        assertThat(failedOnce.getAttempts()).isEqualTo(1);
        assertThat(failedOnce.getLastError()).isEqualTo("ack 대기 시간 초과");
    }

    private OutboxEvent persist(OutboxEvent event) {
        return entityManager.persistAndFlush(event);
    }

    private OutboxEvent pending() {
        return OutboxEvent.create(
                "matching.request",
                "2079:5606",
                UUID.randomUUID().toString(),
                "matching.requested",
                1,
                NOW,
                "{\"request_id\":1}",
                null,
                NOW);
    }
}
