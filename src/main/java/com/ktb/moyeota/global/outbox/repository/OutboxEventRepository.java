package com.ktb.moyeota.global.outbox.repository;

import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxStatus status, Limit limit);

    default List<OutboxEvent> findPending(int batchSize) {
        return findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(batchSize));
    }

    default int markPublished(Collection<Long> ids, LocalDateTime now) {
        return updateStatus(ids, OutboxStatus.PUBLISHED, now, OutboxStatus.PENDING);
    }

    default int recordFailure(Collection<Long> ids, String error) {
        return increaseAttempts(ids, error, OutboxStatus.PENDING);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE OutboxEvent e SET e.status = :published, e.publishedAt = :now
            WHERE e.id IN :ids AND e.status = :pending
            """)
    int updateStatus(@Param("ids") Collection<Long> ids,
                     @Param("published") OutboxStatus published,
                     @Param("now") LocalDateTime now,
                     @Param("pending") OutboxStatus pending);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE OutboxEvent e SET e.attempts = e.attempts + 1, e.lastError = :error
            WHERE e.id IN :ids AND e.status = :pending
            """)
    int increaseAttempts(@Param("ids") Collection<Long> ids,
                         @Param("error") String error,
                         @Param("pending") OutboxStatus pending);
}
