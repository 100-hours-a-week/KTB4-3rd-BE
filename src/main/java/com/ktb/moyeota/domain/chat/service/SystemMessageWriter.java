package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.user.entity.User;

public interface SystemMessageWriter {

    void enter(ChatRoom chatRoom, User joiner, String messageKey);

    void leave(ChatRoom chatRoom, User leaver, String messageKey);

    void rideStarted(ChatRoom chatRoom, String messageKey);

    void rideEnded(ChatRoom chatRoom, String messageKey);

    void requestMeter(ChatRoom chatRoom, String messageKey);

    void requestTransfer(ChatRoom chatRoom, MessageItem.Settlement settlement, String messageKey);

    void completeSettlement(ChatRoom chatRoom, String messageKey);

    void notifyTransferUnverified(ChatRoom chatRoom, String messageKey);
}
