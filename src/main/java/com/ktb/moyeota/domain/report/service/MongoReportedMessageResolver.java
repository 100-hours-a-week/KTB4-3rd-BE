package com.ktb.moyeota.domain.report.service;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mongo")
public class MongoReportedMessageResolver implements ReportedMessageResolver {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Override
    public ReportedMessage resolve(String reportedMessageId) {
        ChatMessage message = chatMessageRepository.findById(parse(reportedMessageId))
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));
        Long companionId = chatRoomRepository.findById(message.getRoomId())
                .map(chatRoom -> chatRoom.getCompanion().getId())
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));
        return ReportedMessage.of(message, companionId);
    }

    private static UUID parse(String reportedMessageId) {
        try {
            return UUID.fromString(reportedMessageId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID);
        }
    }
}
