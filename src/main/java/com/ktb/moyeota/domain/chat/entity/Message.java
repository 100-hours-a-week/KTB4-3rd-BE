package com.ktb.moyeota.domain.chat.entity;

import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(name = "client_message_id", nullable = false)
    private Long clientMessageId; // 멱등키

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(length = 500)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Message(ChatRoom chatRoom, User sender, Long clientMessageId, MessageType messageType, String content){
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.clientMessageId = clientMessageId;
        this.messageType = messageType;
        this.content = content;
    }

    public static Message createGeneralMessage(ChatRoom chatRoom, User sender, Long clientMessageId, String content) {
        return new Message(chatRoom, sender, clientMessageId, MessageType.TEXT, content);
    }

    public static Message joinSystemMessage(ChatRoom chatRoom, User joiner, Long clientMessageId, String content) {
        return new Message(chatRoom, joiner, clientMessageId, MessageType.SYSTEM_JOIN, content);
    }

    public static Message leaveSystemMessage(ChatRoom chatRoom, User leaver, Long clientMessageId, String content) {
        return new Message(chatRoom, leaver, clientMessageId, MessageType.SYSTEM_LEAVE, content);
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
    }

}
