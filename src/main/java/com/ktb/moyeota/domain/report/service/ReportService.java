package com.ktb.moyeota.domain.report.service;

import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.dto.ReportCreateResponse;
import com.ktb.moyeota.domain.report.entity.Report;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.report.entity.UserReport;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.report.repository.ReportRepository;
import com.ktb.moyeota.domain.report.repository.UserReportRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final CompanionParticipantRepository companionParticipantRepository;

    @Transactional
    public ReportCreateResponse create(Long reporterId, ReportCreateRequest request) {
        validateReasonText(request);

        return (request.reportedMessageId() != null)
                ? createMessageReport(reporterId, request)
                : createUserReport(reporterId, request);
    }

    private ReportCreateResponse createMessageReport(Long reporterId, ReportCreateRequest request) {
        Message reportedMessage = messageRepository.findById(request.reportedMessageId())
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));

        Long roomId = reportedMessage.getChatRoom().getId();
        companionParticipantRepository.findActiveByChatRoomIdAndUserId(roomId, reporterId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));

        User reporter = findUser(reporterId);
        User reportedUser = findReportedUser(request.reportedUserId());

        try {
            Report saved = reportRepository.save(Report.createMessageReport(
                    reporter, reportedUser, reportedMessage, request.reason(), request.reasonText()));
            return new ReportCreateResponse(saved.getId(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReportErrorCode.DUPLICATE_MESSAGE_REPORT);
        }
    }

    private ReportCreateResponse createUserReport(Long reporterId, ReportCreateRequest request) {
        User reporter = findUser(reporterId);
        User reportedUser = findReportedUser(request.reportedUserId());

        try {
            UserReport saved = userReportRepository.save(
                    UserReport.create(reporter, reportedUser, request.reason(), request.reasonText()));
            return new ReportCreateResponse(saved.getId(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReportErrorCode.DUPLICATE_USER_REPORT);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private User findReportedUser(Long reportedUserId) {
        return userRepository.findById(reportedUserId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORTED_USER_NOT_FOUND));
    }

    private void validateReasonText(ReportCreateRequest request) {
        boolean etcWithoutText = request.reason() == ReportReason.ETC
                && (request.reasonText() == null || request.reasonText().isBlank());
        if (etcWithoutText) {
            throw new BusinessException(ReportErrorCode.REASON_TEXT_REQUIRED);
        }
    }
}
