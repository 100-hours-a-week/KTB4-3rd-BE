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

    // 최신 방향(아래로 스크롤) 조회. 커서에 가까운 것부터 잘라와야 하므로 ASC로 조회하고, 응답 순서(DESC)는 서비스에서 맞춘다.
    @Query("""
            SELECT m FROM Message m
            LEFT JOIN FETCH m.sender
            WHERE m.chatRoom.id = :roomId
              AND m.id > :cursor
            ORDER BY m.id ASC
            """)
    List<Message> findNewerByChatRoomIdWithSender(
            @Param("roomId") Long roomId, @Param("cursor") Long cursor, Pageable pageable);


    @Query("""
            SELECT m FROM Message m
            WHERE m.chatRoom.id = :roomId
              AND m.clientMessageId = :clientMessageId
            """)
    Optional<Message> findByChatRoomIdAndClientMessageId(
            @Param("roomId") Long roomId, @Param("clientMessageId") String clientMessageId);
}
