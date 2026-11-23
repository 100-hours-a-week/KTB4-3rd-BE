package com.ktb.moyeota.domain.report.service;

import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mysql", matchIfMissing = true)
public class MysqlReportedMessageResolver implements ReportedMessageResolver {

    private final MessageRepository messageRepository;

    @Override
    public ReportedMessage resolve(String reportedMessageId) {
        return messageRepository.findById(parse(reportedMessageId))
                .map(ReportedMessage::of)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));
    }

    private static Long parse(String reportedMessageId) {
        try {
            return Long.valueOf(reportedMessageId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID);
        }
    }
}
