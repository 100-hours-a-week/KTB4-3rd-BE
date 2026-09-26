package com.ktb.moyeota.domain.report.service;

import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.entity.Report;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.report.repository.ReportRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// [주의] 이번 단계는 메시지 신고(reported_message_id 필수)만 지원한다.
// 유저 단독 신고는 uk_reports_message 유니크 제약이 NULL끼리 중복을 못 잡아주는 문제(레이스 컨디션)가
// 해결되지 않아 이번 커밋 범위에서 제외했다 — SQL 보완 방식 확인 후 별도로 추가 예정.
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final CompanionParticipantRepository companionParticipantRepository;

    @Transactional
    public Report create(Long reporterId, ReportCreateRequest request) {
        validateReasonText(request);

        Message reportedMessage = messageRepository.findById(request.reportedMessageId())
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));

        Long roomId = reportedMessage.getChatRoom().getId();
        companionParticipantRepository.findActiveByChatRoomIdAndUserId(roomId, reporterId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_TARGET_INVALID));

        User reporter = findUser(reporterId);
        User reportedUser = findReportedUser(request.reportedUserId());

        try {
            return reportRepository.save(Report.createMessageReport(
                    reporter, reportedUser, reportedMessage, request.reason(), request.reasonText()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReportErrorCode.DUPLICATE_MESSAGE_REPORT);
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

    // reason=ETC일 때 reasonText 필수 — 교차 필드 검증이라 @Valid로 불가하여 서비스 레이어에서 처리한다.
    private void validateReasonText(ReportCreateRequest request) {
        boolean etcWithoutText = request.reason() == ReportReason.ETC
                && (request.reasonText() == null || request.reasonText().isBlank());
        if (etcWithoutText) {
            throw new BusinessException(ReportErrorCode.REASON_TEXT_REQUIRED);
        }
    }
}
