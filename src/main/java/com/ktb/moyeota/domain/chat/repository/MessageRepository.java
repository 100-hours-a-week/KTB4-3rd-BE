package com.ktb.moyeota.domain.chat.repository;

import com.ktb.moyeota.domain.chat.entity.Message;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {


    @Query("""
            SELECT m FROM Message m
            LEFT JOIN FETCH m.sender
            WHERE m.chatRoom.id = :roomId
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<Message> findByChatRoomIdWithSender(
            @Param("roomId") Long roomId, @Param("cursor") Long cursor, Pageable pageable);


    @Query("""
            SELECT m FROM Message m
            WHERE m.chatRoom.id = :roomId
              AND m.clientMessageId = :clientMessageId
            """)
    Optional<Message> findByChatRoomIdAndClientMessageId(
            @Param("roomId") Long roomId, @Param("clientMessageId") Long clientMessageId);
}
