package com.ktb.moyeota.domain.chat.repository;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {

    static final String CLIENT_MESSAGE_INDEX = "uk_chat_messages_room_client";

    private final MongoTemplate mongoTemplate;
    private final int maxSeqAttempts;

    public ChatMessageRepository(MongoTemplate mongoTemplate,
            @Value("${moyeota.chat.max-seq-attempts:10}") int maxSeqAttempts) {
        this.mongoTemplate = mongoTemplate;
        this.maxSeqAttempts = maxSeqAttempts;
    }

    public ChatMessage append(ChatMessage message) {
        for (int attempt = 0; attempt < maxSeqAttempts; attempt++) {
            message.assignSeq(lastSeq(message.getRoomId()) + 1);
            try {
                return mongoTemplate.insert(message);
            } catch (DuplicateKeyException e) {
                if (isClientMessageConflict(e)) {
                    return findByClientMessageId(message.getRoomId(), message.getClientMessageId())
                            .orElseThrow(() -> e);
                }
            }
        }
        throw new BusinessException(ChatErrorCode.MESSAGE_SEQ_CONFLICT);
    }

    public Optional<ChatMessage> findByClientMessageId(Long roomId, String clientMessageId) {
        Query query = new Query(Criteria.where("room_id").is(roomId).and("client_message_id").is(clientMessageId));
        return Optional.ofNullable(mongoTemplate.findOne(query, ChatMessage.class));
    }

    private long lastSeq(Long roomId) {
        Query query = new Query(Criteria.where("room_id").is(roomId))
                .with(Sort.by(Sort.Direction.DESC, "seq"))
                .limit(1);
        query.fields().include("seq");
        ChatMessage last = mongoTemplate.findOne(query, ChatMessage.class);
        return last == null ? 0L : last.getSeq();
    }

    private static boolean isClientMessageConflict(DuplicateKeyException e) {
        return e.getMessage() != null && e.getMessage().contains(CLIENT_MESSAGE_INDEX);
    }
}
