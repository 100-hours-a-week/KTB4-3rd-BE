package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Settlement;
import com.ktb.moyeota.domain.chat.dto.MessageItem.SystemActor;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.entity.SystemMessageText;
import com.ktb.moyeota.domain.chat.event.ChatMessageAppendedEvent;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mongo")
public class MongoSystemMessageWriter implements SystemMessageWriter {

    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public void enter(ChatRoom chatRoom, User joiner, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_JOIN, joiner, null, messageKey, null);
    }

    @Override
    public void leave(ChatRoom chatRoom, User leaver, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_LEAVE, leaver, null, messageKey, null);
    }

    @Override
    public void rideStarted(ChatRoom chatRoom, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_RIDE_STARTED, null, null, messageKey, null);
    }

    @Override
    public void rideEnded(ChatRoom chatRoom, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_RIDE_ENDED, null, null, messageKey, null);
    }

    @Override
    public void requestMeter(ChatRoom chatRoom, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_METER_REQUESTED, null,
                SystemMessageText.METER_REQUESTED.getText(), messageKey, null);
    }

    @Override
    public void requestTransfer(ChatRoom chatRoom, Settlement settlement, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_TRANSFER_REQUESTED, null,
                SystemMessageText.TRANSFER_REQUESTED.getText(), messageKey, settlement);
    }

    @Override
    public void completeSettlement(ChatRoom chatRoom, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_SETTLEMENT_COMPLETED, null,
                SystemMessageText.SETTLEMENT_COMPLETED.getText(), messageKey, null);
    }

    @Override
    public void notifyTransferUnverified(ChatRoom chatRoom, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_TRANSFER_UNVERIFIED, null,
                SystemMessageText.TRANSFER_UNVERIFIED.getText(), messageKey, null);
    }

    @Override
    public void transferDone(ChatRoom chatRoom, User transferrer, int amount, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_TRANSFER_DONE, transferrer, null, messageKey, null, amount);
    }

    @Override
    public void requestSettlementConfirm(ChatRoom chatRoom, String messageKey) {
        appendOnce(chatRoom, MessageType.SYSTEM_SETTLEMENT_CONFIRM_REQUESTED, null,
                SystemMessageText.SETTLEMENT_CONFIRM_REQUESTED.getText(), messageKey, null);
    }

    public void requestRideStart(ChatRoom chatRoom) {
        appendOnce(chatRoom, MessageType.SYSTEM_RIDE_START_REQUESTED, null, null, SystemMessageKeys.forCompanion(
                chatRoom.getCompanion().getId(), MessageType.SYSTEM_RIDE_START_REQUESTED), null);
    }

    public void requestRideEnd(ChatRoom chatRoom) {
        appendOnce(chatRoom, MessageType.SYSTEM_RIDE_END_REQUESTED, null, null, SystemMessageKeys.forCompanion(
                chatRoom.getCompanion().getId(), MessageType.SYSTEM_RIDE_END_REQUESTED), null);
    }

    private void appendOnce(ChatRoom chatRoom, MessageType type, User actor, String content, String messageKey,
            Settlement settlement) {
        appendOnce(chatRoom, type, actor, content, messageKey, settlement, null);
    }

    private void appendOnce(ChatRoom chatRoom, MessageType type, User actor, String content, String messageKey,
            Settlement settlement, Integer transferAmount) {
        if (chatMessageRepository.findByClientMessageId(chatRoom.getId(), messageKey).isPresent()) {
            return;
        }
        ChatMessage saved = chatMessageRepository.append(ChatMessage.system(chatRoom.getId(), type,
                actor == null ? null : actor.getId(), messageKey, content, clock.instant()));
        SystemActor systemActor = actor == null ? null : new SystemActor(actor.getId(), actor.getName());
        eventPublisher.publishEvent(new ChatMessageAppendedEvent(chatRoom.getId(),
                ChatMessageItem.system(saved, systemActor, settlement, transferAmount, clock.getZone())));
    }
}
