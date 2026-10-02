package com.ktb.moyeota.domain.report.service;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.dto.ReportCreateResponse;
import com.ktb.moyeota.domain.report.entity.MessageReport;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.report.entity.UserReport;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.report.repository.MessageReportRepository;
import com.ktb.moyeota.domain.report.repository.UserReportRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Duration REPORT_PERIOD_AFTER_RIDE = Duration.ofHours(72);

    private final MessageReportRepository messageReportRepository;
    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final EntityManager entityManager;
    private final Clock clock;

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

        validateReporter(reportedMessage.getChatRoom().getCompanion().getId(), reporterId);

        User reporter = findUser(reporterId);
        User reportedUser = findReportedUser(request.reportedUserId());
        validateNotSelf(reporter, reportedUser);
        if (reportedMessage.getSender() == null || !reportedMessage.getSender().getId().equals(reportedUser.getId())) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID);
        }

        try {
            MessageReport saved = messageReportRepository.save(MessageReport.create(
                    reporter, reportedUser, reportedMessage, request.reason(), request.reasonText()));
            return new ReportCreateResponse(saved.getId(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReportErrorCode.DUPLICATE_MESSAGE_REPORT);
        }
    }

    private ReportCreateResponse createUserReport(Long reporterId, ReportCreateRequest request) {
        validateReporter(request.companionId(), reporterId);

        User reporter = findUser(reporterId);
        User reportedUser = findReportedUser(request.reportedUserId());
        validateNotSelf(reporter, reportedUser);
        if (!companionParticipantRepository.existsByCompanionIdAndUserId(request.companionId(), reportedUser.getId())) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID);
        }
        Companion companion = entityManager.getReference(Companion.class, request.companionId());

        try {
            UserReport saved = userReportRepository.save(
                    UserReport.create(reporter, reportedUser, companion, request.reason(), request.reasonText()));
            return new ReportCreateResponse(saved.getId(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReportErrorCode.DUPLICATE_USER_REPORT);
        }
    }

    private void validateReporter(Long companionId, Long reporterId) {
        CompanionParticipant participation = companionParticipantRepository
                .findByCompanionIdAndUserId(companionId, reporterId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));
        switch (participation.getOutcomeStatus()) {
            case PENDING -> {
            }
            case COMPLETED -> {
                LocalDateTime deadline = participation.getLeftAt().plus(REPORT_PERIOD_AFTER_RIDE);
                if (!LocalDateTime.now(clock).isBefore(deadline)) {
                    throw new BusinessException(ReportErrorCode.REPORT_PERIOD_EXPIRED);
                }
            }
            case INCOMPLETE -> throw new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID);
        }
    }

    private static void validateNotSelf(User reporter, User reportedUser) {
        if (reporter.getId().equals(reportedUser.getId())) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID);
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
