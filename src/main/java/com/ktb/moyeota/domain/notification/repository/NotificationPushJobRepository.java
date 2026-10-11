package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface NotificationPushJobRepository extends JpaRepository<NotificationPushJob, Long> {

    String SKIP_LOCKED = "-2";

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationPushJob j
            SET j.status = com.ktb.moyeota.domain.notification.entity.PushJobStatus.SENDING,
                j.attempts = j.attempts + 1,
                j.nextAttemptAt = :leaseUntil,
                j.updatedAt = :now
            WHERE j.id = :id
              AND j.status = com.ktb.moyeota.domain.notification.entity.PushJobStatus.PENDING
            """)
    int claimPending(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
    @Query("""
            SELECT j
            FROM NotificationPushJob j
            WHERE j.status IN :statuses
              AND j.nextAttemptAt <= :now
            ORDER BY j.id
            """)
    List<NotificationPushJob> findClaimableForUpdate(
            @Param("statuses") Collection<PushJobStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE NotificationPushJob j
            SET j.status = com.ktb.moyeota.domain.notification.entity.PushJobStatus.SENDING,
                j.attempts = j.attempts + 1,
                j.nextAttemptAt = :leaseUntil,
                j.updatedAt = :now
            WHERE j.id IN :ids
            """)
    int claimAll(
            @Param("ids") Collection<Long> ids,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);
}
