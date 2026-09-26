package com.ktb.moyeota.domain.chat.repository;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionParticipantRepository extends JpaRepository<CompanionParticipant, Long> {


    @Query(nativeQuery = true, value = """
            SELECT pp.* FROM companion_participants pp
            JOIN chat_rooms cr ON cr.companion_id = pp.companion_id
            WHERE cr.id = :roomId
              AND pp.user_id = :userId
              AND pp.left_at IS NULL
            """)
    Optional<CompanionParticipant> findActiveByChatRoomIdAndUserId(
            @Param("roomId") Long roomId, @Param("userId") Long userId);


    @Modifying
    @Query("""
            UPDATE CompanionParticipant p
            SET p.message = :message
            WHERE p.id = :participantId
              AND (p.message IS NULL OR p.message.id < :newMessageId)
            """)
    int updateLastReadMessageIfNewer(
            @Param("participantId") Long participantId,
            @Param("message") Message message,
            @Param("newMessageId") Long newMessageId);


    @Query("""
            SELECT p FROM CompanionParticipant p
            WHERE p.companion.id = :companionId
              AND p.user.id = :userId
              AND p.leftAt IS NULL
            """)
    Optional<CompanionParticipant> findActiveByCompanionIdAndUserId(
            @Param("companionId") Long companionId, @Param("userId") Long userId);

    Optional<CompanionParticipant> findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(
            Long companionId, Long userId);
}
