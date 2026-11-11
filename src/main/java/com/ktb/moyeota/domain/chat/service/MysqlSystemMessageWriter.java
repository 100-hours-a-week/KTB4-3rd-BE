package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mysql", matchIfMissing = true)
public class MysqlSystemMessageWriter implements SystemMessageWriter {

    private final ChatSystemMessageService chatSystemMessageService;

    @Override
    public void enter(ChatRoom chatRoom, User joiner, String messageKey) {
        chatSystemMessageService.enter(chatRoom, joiner, messageKey);
    }

    @Override
    public void leave(ChatRoom chatRoom, User leaver, String messageKey) {
        chatSystemMessageService.leave(chatRoom, leaver, messageKey);
    }

    @Override
    public void rideStarted(ChatRoom chatRoom, String messageKey) {
        chatSystemMessageService.rideStarted(chatRoom, messageKey);
    }

    @Override
    public void rideEnded(ChatRoom chatRoom, String messageKey) {
        chatSystemMessageService.rideEnded(chatRoom, messageKey);
    }

    @Override
    public void requestMeter(ChatRoom chatRoom, String messageKey) {
        chatSystemMessageService.requestMeter(chatRoom, messageKey);
    }

    @Override
    public void requestTransfer(ChatRoom chatRoom, MessageItem.Settlement settlement, String messageKey) {
        chatSystemMessageService.requestTransfer(chatRoom, settlement, messageKey);
    }

    @Override
    public void completeSettlement(ChatRoom chatRoom, String messageKey) {
        chatSystemMessageService.completeSettlement(chatRoom, messageKey);
    }

    @Override
    public void notifyTransferUnverified(ChatRoom chatRoom, String messageKey) {
        chatSystemMessageService.notifyTransferUnverified(chatRoom, messageKey);
    }
}
