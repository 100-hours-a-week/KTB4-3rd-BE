package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.recipient.id = :recipientId
              AND (:unreadOnly = false OR n.readAt IS NULL)
              AND (:cursor IS NULL OR n.id < :cursor)
            ORDER BY n.id DESC
            """)
    List<Notification> findByRecipientWithCursor(
            @Param("recipientId") Long recipientId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.id = :id
              AND n.recipient.id = :recipientId
              AND n.readAt IS NULL
            """)
    int markReadIfUnread(
            @Param("id") Long id,
            @Param("recipientId") Long recipientId,
            @Param("readAt") LocalDateTime readAt);

    @Query("""
            SELECT n.readAt
            FROM Notification n
            WHERE n.id = :id
              AND n.recipient.id = :recipientId
            """)
    Optional<LocalDateTime> findReadAtByIdAndRecipientId(
            @Param("id") Long id,
            @Param("recipientId") Long recipientId);
}
