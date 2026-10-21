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

import static com.ktb.moyeota.domain.chat.entity.Message.rideStartRequestedSystemMessage;

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
        publish(chatRoom, rideStartRequestedSystemMessage(chatRoom, newSystemMessageKey(), null));
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

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestMeter(ChatRoom chatRoom) {
        publish(chatRoom, Message.meterRequestedSystemMessage(chatRoom, newSystemMessageKey(), "택시 미터기 사진을 보내주세요"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestTransfer(ChatRoom chatRoom, MessageItem.Settlement settlement) {
        publish(chatRoom,
                Message.transferRequestedSystemMessage(chatRoom, newSystemMessageKey(), "방장이 정산을 요청했어요"),
                settlement);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeSettlement(ChatRoom chatRoom) {
        publish(chatRoom, Message.settlementCompletedSystemMessage(chatRoom, newSystemMessageKey(), "정산이 완료됐어요"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyTransferUnverified(ChatRoom chatRoom) {
        publish(chatRoom, Message.transferUnverifiedSystemMessage(chatRoom, newSystemMessageKey(),
                "송금 확인이 어려워요. 대면으로 송금 내역을 확인한 뒤 방장이 정산을 완료해주세요"));
    }

    private void publish(ChatRoom chatRoom, Message message) {
        publish(chatRoom, message, null);
    }

    private void publish(ChatRoom chatRoom, Message message, MessageItem.Settlement settlement) {
        Message saved = messageRepository.save(message);
        chatRoom.updateLastMessageId(saved.getId());
        eventPublisher.publishEvent(
                new ChatMessageCreatedEvent(chatRoom.getId(), MessageItem.from(saved, null, settlement)));
    }

    private static String newSystemMessageKey() {
        return UUID.randomUUID().toString();
    }
}
