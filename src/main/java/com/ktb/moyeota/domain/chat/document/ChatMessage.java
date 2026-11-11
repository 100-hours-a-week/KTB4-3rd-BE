package com.ktb.moyeota.domain.chat.document;

import com.ktb.moyeota.domain.chat.entity.MessageType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    private UUID id;

    @Field("room_id")
    private Long roomId;

    private long seq;

    private MessageType type;

    @Field("sender_id")
    private Long senderId;

    @Field("client_message_id")
    private String clientMessageId;

    private String content;

    @Field("created_at")
    private Instant createdAt;

    private ChatMessage(Long roomId, MessageType type, Long senderId, String clientMessageId, String content,
            Instant createdAt) {
        this.id = UuidV7.at(createdAt);
        this.roomId = roomId;
        this.type = type;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static ChatMessage text(Long roomId, Long senderId, String clientMessageId, String content,
            Instant createdAt) {
        return new ChatMessage(roomId, MessageType.TEXT, senderId, clientMessageId, content, createdAt);
    }

    public static ChatMessage system(Long roomId, MessageType type, Long actorId, String clientMessageId,
            String content, Instant createdAt) {
        if (type == MessageType.TEXT) {
            throw new IllegalArgumentException("시스템 메시지 종류가 아닙니다. type=" + type);
        }
        return new ChatMessage(roomId, type, actorId, clientMessageId, content, createdAt);
    }

    public void assignSeq(long seq) {
        this.seq = seq;
    }
}
