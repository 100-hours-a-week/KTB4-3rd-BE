package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSystemMessageService {

    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enter(ChatRoom chatRoom, User joiner) {
        Message saved = messageRepository.save(
                Message.joinSystemMessage(chatRoom, joiner, newSystemMessageKey(), null));
        chatRoom.updateLastMessageId(saved.getId());
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(chatRoom.getId(), MessageItem.from(saved)));
    }

    private static long newSystemMessageKey() {
        return -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
