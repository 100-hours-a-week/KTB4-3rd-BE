package com.ktb.moyeota.domain.chat.repository;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {


    Optional<ChatRoom> findByCompanionId(Long companionId);


    @Query("""
            SELECT cr FROM ChatRoom cr
            JOIN FETCH cr.companion c
            WHERE cr.id = :roomId
            """)
    Optional<ChatRoom> findByIdWithCompanion(@Param("roomId") Long roomId);


    @Query(nativeQuery = true, value = """
            SELECT * FROM (
                SELECT cr.id AS id,
                       c.id AS companionId,
                       c.kind AS kind,
                       c.origin_name AS originName,
                       c.departure_at AS departureAt,
                       c.current_count AS currentCount,
                       c.capacity AS capacity,
                       u.profile_image_url AS hostProfileImageUrl,
                       cr.last_message_id AS lastMessageId,
                       pp.last_read_message_id AS lastReadMessageId,
                       m.created_at AS lastMessageAt
                FROM companion_participants pp
                JOIN chat_rooms cr ON cr.companion_id = pp.companion_id
                JOIN companions c ON c.id = cr.companion_id
                JOIN users u ON u.id = c.host_id
                LEFT JOIN messages m ON m.id = cr.last_message_id
                WHERE pp.user_id = :userId
                  AND pp.left_at IS NULL
                  AND (:kind IS NULL OR c.kind = :kind)
            ) t
            WHERE :cursorRoomId IS NULL
               OR t.lastMessageAt < :cursorLastMessageAt
               OR (t.lastMessageAt = :cursorLastMessageAt AND t.id < :cursorRoomId)
            ORDER BY t.lastMessageAt DESC, t.id DESC
            LIMIT :n
            """)
    List<ChatRoomListProjection> findMyChatRooms(
            @Param("userId") Long userId,
            @Param("kind") String kind,
            @Param("cursorLastMessageAt") LocalDateTime cursorLastMessageAt,
            @Param("cursorRoomId") Long cursorRoomId,
            @Param("n") int n);

    @Modifying
    @Query("""
            UPDATE Companion c
            SET c.currentCount = c.currentCount + 1
            WHERE c.id = :id
              AND c.status = :status
              AND c.currentCount < c.capacity
            """)
    int increaseCurrentCountIfRecruitingAndNotFull(@Param("id") Long id, @Param("status") CompanionStatus status);

    @Modifying
    @Query("UPDATE Companion c SET c.currentCount = c.currentCount - 1 WHERE c.id = :id AND c.currentCount > 0")
    int decreaseCurrentCount(@Param("id") Long id);
}
