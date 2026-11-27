package com.ktb.moyeota.domain.report.service;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.report.entity.MessageReport;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.user.entity.User;

public record ReportedMessage(
        Long companionId,
        MessageType type,
        Long senderId,
        Message message,
        String chatMessageId
) {

    public static ReportedMessage of(Message message) {
        Long senderId = message.getSender() == null ? null : message.getSender().getId();
        return new ReportedMessage(message.getChatRoom().getCompanion().getId(), message.getMessageType(), senderId,
                message, null);
    }

    public static ReportedMessage of(ChatMessage message, Long companionId) {
        return new ReportedMessage(companionId, message.getType(), message.getSenderId(), null,
                message.getId().toString());
    }

    public MessageReport toReport(User reporter, User reportedUser, ReportReason reason, String reasonText) {
        if (message != null) {
            return MessageReport.create(reporter, reportedUser, message, reason, reasonText);
        }
        return MessageReport.createForChatMessage(reporter, reportedUser, chatMessageId, reason, reasonText);
    }
}
