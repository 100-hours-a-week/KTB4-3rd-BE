package com.ktb.moyeota.domain.chat.entity;

import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_messages_room_client", columnNames = {"room_id", "client_message_id"}))
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

    @Column(name = "client_message_id", nullable = false, length = 36)
    private String clientMessageId; // 멱등키(UUID)

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(length = 500)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Message(ChatRoom chatRoom, User sender, String clientMessageId, MessageType messageType, String content){
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.clientMessageId = clientMessageId;
        this.messageType = messageType;
        this.content = content;
    }

    public static Message createGeneralMessage(ChatRoom chatRoom, User sender, String clientMessageId, String content) {
        return new Message(chatRoom, sender, clientMessageId, MessageType.TEXT, content);
    }

    public static Message joinSystemMessage(ChatRoom chatRoom, User joiner, String clientMessageId, String content) {
        return new Message(chatRoom, joiner, clientMessageId, MessageType.SYSTEM_JOIN, content);
    }

    public static Message leaveSystemMessage(ChatRoom chatRoom, User leaver, String clientMessageId, String content) {
        return new Message(chatRoom, leaver, clientMessageId, MessageType.SYSTEM_LEAVE, content);
    }

    public static Message rideStartedSystemMessage(ChatRoom chatRoom, String clientMessageId, String content) {
        return new Message(chatRoom, null, clientMessageId, MessageType.SYSTEM_RIDE_STARTED, content);
    }

    public static Message rideEndedSystemMessage(ChatRoom chatRoom, String clientMessageId, String content) {
        return new Message(chatRoom, null, clientMessageId, MessageType.SYSTEM_RIDE_ENDED, content);
    }

    public static Message rideStartRequestedSystemMessage(ChatRoom chatRoom, String clientMessageId, String content) {
        return new Message(chatRoom, null, clientMessageId, MessageType.SYSTEM_RIDE_START_REQUESTED, content);
    }

    public static Message rideEndRequestedSystemMessage(ChatRoom chatRoom, String clientMessageId, String content) {
        return new Message(chatRoom, null, clientMessageId, MessageType.SYSTEM_RIDE_END_REQUESTED, content);
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
    }

}
