package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.SystemMessageText;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.ktb.moyeota.domain.chat.entity.Message.rideStartRequestedSystemMessage;

@Service
@RequiredArgsConstructor
public class ChatSystemMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<MongoSystemMessageWriter> mongoSystemMessageWriter;

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
        MongoSystemMessageWriter mongoWriter = mongoSystemMessageWriter.getIfAvailable();
        if (mongoWriter != null) {
            mongoWriter.requestRideStart(chatRoom);
            return;
        }
        publish(chatRoom, rideStartRequestedSystemMessage(chatRoom, newSystemMessageKey(), null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestRideEnd(ChatRoom chatRoom) {
        MongoSystemMessageWriter mongoWriter = mongoSystemMessageWriter.getIfAvailable();
        if (mongoWriter != null) {
            mongoWriter.requestRideEnd(chatRoom);
            return;
        }
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

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestMeter(ChatRoom chatRoom) {
        publish(chatRoom, Message.meterRequestedSystemMessage(chatRoom, newSystemMessageKey(),
                SystemMessageText.METER_REQUESTED.getText()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestTransfer(ChatRoom chatRoom, MessageItem.Settlement settlement) {
        publish(chatRoom,
                Message.transferRequestedSystemMessage(chatRoom, newSystemMessageKey(),
                        SystemMessageText.TRANSFER_REQUESTED.getText()),
                settlement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeSettlement(ChatRoom chatRoom) {
        publish(chatRoom, Message.settlementCompletedSystemMessage(chatRoom, newSystemMessageKey(),
                SystemMessageText.SETTLEMENT_COMPLETED.getText()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyTransferUnverified(ChatRoom chatRoom) {
        publish(chatRoom, Message.transferUnverifiedSystemMessage(chatRoom, newSystemMessageKey(),
                SystemMessageText.TRANSFER_UNVERIFIED.getText()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enter(ChatRoom chatRoom, User joiner, String messageKey) {
        publishOnce(chatRoom, Message.joinSystemMessage(chatRoom, joiner, messageKey, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void leave(ChatRoom chatRoom, User leaver, String messageKey) {
        publishOnce(chatRoom, Message.leaveSystemMessage(chatRoom, leaver, messageKey, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rideStarted(ChatRoom chatRoom, String messageKey) {
        publishOnce(chatRoom, Message.rideStartedSystemMessage(chatRoom, messageKey, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rideEnded(ChatRoom chatRoom, String messageKey) {
        publishOnce(chatRoom, Message.rideEndedSystemMessage(chatRoom, messageKey, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestMeter(ChatRoom chatRoom, String messageKey) {
        publishOnce(chatRoom, Message.meterRequestedSystemMessage(chatRoom, messageKey,
                SystemMessageText.METER_REQUESTED.getText()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestTransfer(ChatRoom chatRoom, MessageItem.Settlement settlement, String messageKey) {
        publishOnce(chatRoom, Message.transferRequestedSystemMessage(chatRoom, messageKey,
                SystemMessageText.TRANSFER_REQUESTED.getText()), settlement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeSettlement(ChatRoom chatRoom, String messageKey) {
        publishOnce(chatRoom, Message.settlementCompletedSystemMessage(chatRoom, messageKey,
                SystemMessageText.SETTLEMENT_COMPLETED.getText()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyTransferUnverified(ChatRoom chatRoom, String messageKey) {
        publishOnce(chatRoom, Message.transferUnverifiedSystemMessage(chatRoom, messageKey,
                SystemMessageText.TRANSFER_UNVERIFIED.getText()));
    }

    private void publishOnce(ChatRoom chatRoom, Message message) {
        publishOnce(chatRoom, message, null);
    }

    private void publishOnce(ChatRoom chatRoom, Message message, MessageItem.Settlement settlement) {
        chatRoomRepository.findByIdForUpdate(chatRoom.getId());
        if (messageRepository.existsByChatRoomIdAndClientMessageId(chatRoom.getId(), message.getClientMessageId())) {
            return;
        }
        save(chatRoom, message, settlement);
    }

    private void publish(ChatRoom chatRoom, Message message) {
        publish(chatRoom, message, null);
    }

    private void publish(ChatRoom chatRoom, Message message, MessageItem.Settlement settlement) {
        chatRoomRepository.findByIdForUpdate(chatRoom.getId());
        save(chatRoom, message, settlement);
    }

    private void save(ChatRoom chatRoom, Message message, MessageItem.Settlement settlement) {
        Message saved = messageRepository.save(message);
        chatRoom.updateLastMessageId(saved.getId());
        eventPublisher.publishEvent(
                new ChatMessageCreatedEvent(chatRoom.getId(), MessageItem.from(saved, null, settlement)));
    }

    private static String newSystemMessageKey() {
        return UUID.randomUUID().toString();
    }
}
