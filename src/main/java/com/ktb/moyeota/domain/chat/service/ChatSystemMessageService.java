package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.util.UUID;
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
        publish(chatRoom, Message.joinSystemMessage(chatRoom, joiner, newSystemMessageKey(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void leave(ChatRoom chatRoom, User leaver) {
        publish(chatRoom, Message.leaveSystemMessage(chatRoom, leaver, newSystemMessageKey(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestRideStart(ChatRoom chatRoom) {
        publish(chatRoom, Message.rideStartRequestedSystemMessage(chatRoom, newSystemMessageKey(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestRideEnd(ChatRoom chatRoom) {
        publish(chatRoom, Message.rideEndRequestedSystemMessage(chatRoom, newSystemMessageKey(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rideStarted(ChatRoom chatRoom) {
        publish(chatRoom, Message.rideStartedSystemMessage(chatRoom, newSystemMessageKey(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rideEnded(ChatRoom chatRoom) {
        publish(chatRoom, Message.rideEndedSystemMessage(chatRoom, newSystemMessageKey(), null));
    }

    private void publish(ChatRoom chatRoom, Message message) {
        Message saved = messageRepository.save(message);
        chatRoom.updateLastMessageId(saved.getId());
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(chatRoom.getId(), MessageItem.from(saved)));
    }

    private static String newSystemMessageKey() {
        return UUID.randomUUID().toString();
    }
}
