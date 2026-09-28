package com.ktb.moyeota.domain.chat.entity;

import com.ktb.moyeota.domain.companion.entity.Companion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_id")
    private Long lastMessageId; // 읽음 처리용

    private ChatRoom(Companion companion, LocalDateTime closedAt, LocalDateTime createdAt, Long lastMessageId){
        this.companion = companion;
        this.closedAt = closedAt;
        this.createdAt = createdAt;
        this.lastMessageId = lastMessageId;
    }

    public static ChatRoom create(Companion companion) {
        return new ChatRoom(companion, null, LocalDateTime.now(), null);
    }

    public void updateLastMessageId(Long messageId) {
        this.lastMessageId = messageId;
    }

    public void close() {
        this.closedAt = LocalDateTime.now();
    }

}
