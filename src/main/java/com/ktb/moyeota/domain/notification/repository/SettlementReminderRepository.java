package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SettlementReminderRepository extends Repository<Settlement, Long> {

    @Query("""
            SELECT s.id
            FROM Settlement s
            WHERE s.status = com.ktb.moyeota.domain.settlement.entity.SettlementStatus.REQUESTED
              AND s.companion.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
              AND s.companion.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED
            ORDER BY s.id
            """)
    List<Long> findSettlementIdsToRemind();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s.companion.id
            FROM Settlement s
            WHERE s.id = :id
              AND s.status = com.ktb.moyeota.domain.settlement.entity.SettlementStatus.REQUESTED
            """)
    Optional<Long> findRequestedCompanionIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT p.user.id
            FROM CompanionParticipant p
            WHERE p.companion.id = :companionId
              AND p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING
            ORDER BY p.id
            """)
    List<Long> findPendingRecipientIds(@Param("companionId") Long companionId);

    @Query("""
            SELECT new com.ktb.moyeota.domain.notification.repository.ReminderRoom(r.id, c.host.id)
            FROM ChatRoom r
            JOIN r.companion c
            WHERE c.id = :companionId
            """)
    Optional<ReminderRoom> findReminderRoom(@Param("companionId") Long companionId);

    @Query("""
            SELECT new com.ktb.moyeota.domain.notification.repository.ReminderState(
                n.id, n.recipient.id, n.createdAt, j.id, j.status)
            FROM Notification n
            LEFT JOIN NotificationPushJob j ON j.notification = n
            WHERE n.recipient.id IN :recipientIds
              AND n.type = com.ktb.moyeota.domain.notification.entity.NotificationType.SETTLEMENT_COMPLETION_REMINDER
              AND n.eventId = :settlementId
            """)
    List<ReminderState> findReminders(@Param("recipientIds") Collection<Long> recipientIds,
                                      @Param("settlementId") Long settlementId);
}
