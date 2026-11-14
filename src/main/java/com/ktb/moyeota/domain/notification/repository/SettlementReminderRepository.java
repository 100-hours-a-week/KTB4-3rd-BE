package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
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
            SELECT s
            FROM Settlement s
            WHERE s.id = :id
              AND s.status = com.ktb.moyeota.domain.settlement.entity.SettlementStatus.REQUESTED
            """)
    Optional<Settlement> findRequestedForUpdate(@Param("id") Long id);

    @Query("""
            SELECT p
            FROM CompanionParticipant p
            JOIN FETCH p.user
            WHERE p.companion.id = :companionId
              AND p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING
            ORDER BY p.id
            """)
    List<CompanionParticipant> findPendingParticipants(@Param("companionId") Long companionId);

    @Query("""
            SELECT r
            FROM ChatRoom r
            JOIN FETCH r.companion
            WHERE r.companion.id = :companionId
            """)
    Optional<ChatRoom> findChatRoom(@Param("companionId") Long companionId);

    @Query("""
            SELECT new com.ktb.moyeota.domain.notification.repository.ExistingReminder(n, j)
            FROM Notification n
            LEFT JOIN NotificationPushJob j ON j.notification = n
            WHERE n.recipient.id = :recipientId
              AND n.type = com.ktb.moyeota.domain.notification.entity.NotificationType.SETTLEMENT_COMPLETION_REMINDER
              AND n.eventId = :settlementId
            """)
    Optional<ExistingReminder> findReminder(@Param("recipientId") Long recipientId,
                                            @Param("settlementId") Long settlementId);
}
