package com.ktb.moyeota.domain.report.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long REPORTER_ID = 42L;
    private static final Long REPORTED_USER_ID = 7L;
    private static final Long MESSAGE_ID = 1441L;
    private static final Long ROOM_ID = 501L;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserReportRepository userReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @InjectMocks
    private ReportService service;

    private User reporter;
    private User reportedUser;
    private Message reportedMessage;
    private CompanionParticipant participant;

    @BeforeEach
    void setUp() {
        reporter = user(REPORTER_ID, "우림");
        reportedUser = user(REPORTED_USER_ID, "상대방");

        Companion companion = companionPost(reportedUser);
        ChatRoom chatRoom = ChatRoom.create(companion);
        ReflectionTestUtils.setField(chatRoom, "id", ROOM_ID);

        reportedMessage = Message.createGeneralMessage(chatRoom, reportedUser, 1L, "부적절한 내용");
        ReflectionTestUtils.setField(reportedMessage, "id", MESSAGE_ID);

        participant = CompanionParticipant.join(companion, reporter);
    }

    private ReportCreateRequest messageReportRequest(ReportReason reason, String reasonText) {
        return new ReportCreateRequest(REPORTED_USER_ID, MESSAGE_ID, reason, reasonText);
    }

    private ReportCreateRequest userReportRequest(ReportReason reason, String reasonText) {
        return new ReportCreateRequest(REPORTED_USER_ID, null, reason, reasonText);
    }

    private void stubHappyPath() {
        given(messageRepository.findById(MESSAGE_ID)).willReturn(Optional.of(reportedMessage));
        given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, REPORTER_ID))
                .willReturn(Optional.of(participant));
        given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
        given(userRepository.findById(REPORTED_USER_ID)).willReturn(Optional.of(reportedUser));
    }

    @Nested
    @DisplayName("메시지 신고")
    class MessageReport {

        @Test
        @DisplayName("참여자가 방의 메시지를 신고하면 저장된다")
        void createsReport() {
            stubHappyPath();

            Report saved = Report.createMessageReport(
                    reporter, reportedUser, reportedMessage, ReportReason.ABUSE, null);
            ReflectionTestUtils.setField(saved, "id", 900L);
            given(reportRepository.save(any(Report.class))).willReturn(saved);

            ReportCreateResponse result = service.create(REPORTER_ID, messageReportRequest(ReportReason.ABUSE, null));

            assertThat(result.id()).isEqualTo(900L);
        }

        @Test
        @DisplayName("신고 대상 메시지가 없으면 REPORT_TARGET_INVALID다")
        void messageNotFound() {
            given(messageRepository.findById(MESSAGE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(REPORTER_ID, messageReportRequest(ReportReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.REPORT_TARGET_INVALID);

            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("신고자가 그 방의 참여자가 아니면 REPORT_TARGET_INVALID다")
        void notParticipant() {
            given(messageRepository.findById(MESSAGE_ID)).willReturn(Optional.of(reportedMessage));
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, REPORTER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(REPORTER_ID, messageReportRequest(ReportReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.REPORT_TARGET_INVALID);
        }

        @Test
        @DisplayName("신고 대상 유저가 없으면 REPORTED_USER_NOT_FOUND다")
        void reportedUserNotFound() {
            given(messageRepository.findById(MESSAGE_ID)).willReturn(Optional.of(reportedMessage));
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, REPORTER_ID))
                    .willReturn(Optional.of(participant));
            given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
            given(userRepository.findById(REPORTED_USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(REPORTER_ID, messageReportRequest(ReportReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.REPORTED_USER_NOT_FOUND);
        }

        @Test
        @DisplayName("같은 메시지를 다시 신고하면 DB 유니크 제약 위반을 DUPLICATE_MESSAGE_REPORT로 변환한다")
        void duplicateMessageReport() {
            stubHappyPath();
            given(reportRepository.save(any(Report.class)))
                    .willThrow(new DataIntegrityViolationException("uk_reports_message"));

            assertThatThrownBy(() -> service.create(REPORTER_ID, messageReportRequest(ReportReason.ABUSE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.DUPLICATE_MESSAGE_REPORT);
        }
    }

    @Nested
    @DisplayName("유저 단독 신고")
    class UserReport {

        @Test
        @DisplayName("메시지 없이 유저만 신고하면 저장된다")
        void createsReport() {
            given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
            given(userRepository.findById(REPORTED_USER_ID)).willReturn(Optional.of(reportedUser));

            UserReport saved = UserReport.create(reporter, reportedUser, ReportReason.NO_SHOW, null);
            ReflectionTestUtils.setField(saved, "id", 901L);
            given(userReportRepository.save(any(UserReport.class))).willReturn(saved);

            ReportCreateResponse result = service.create(REPORTER_ID, userReportRequest(ReportReason.NO_SHOW, null));

            assertThat(result.id()).isEqualTo(901L);
            verify(messageRepository, never()).findById(any());
        }

        @Test
        @DisplayName("신고 대상 유저가 없으면 REPORTED_USER_NOT_FOUND다")
        void reportedUserNotFound() {
            given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
            given(userRepository.findById(REPORTED_USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(REPORTER_ID, userReportRequest(ReportReason.NO_SHOW, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.REPORTED_USER_NOT_FOUND);

            verify(userReportRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 같은 유저를 신고했으면 DB 유니크 제약 위반을 DUPLICATE_USER_REPORT로 변환한다")
        void duplicateUserReport() {
            given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
            given(userRepository.findById(REPORTED_USER_ID)).willReturn(Optional.of(reportedUser));
            given(userReportRepository.save(any(UserReport.class)))
                    .willThrow(new DataIntegrityViolationException("uk_user_reports_target"));

            assertThatThrownBy(() -> service.create(REPORTER_ID, userReportRequest(ReportReason.NO_SHOW, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.DUPLICATE_USER_REPORT);
        }
    }

    @Nested
    @DisplayName("reason=ETC 교차 검증")
    class ReasonTextValidation {

        @Test
        @DisplayName("reason=ETC인데 reasonText가 없으면 REASON_TEXT_REQUIRED다")
        void requiresReasonTextForEtc() {
            assertThatThrownBy(() -> service.create(REPORTER_ID, messageReportRequest(ReportReason.ETC, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.REASON_TEXT_REQUIRED);

            verify(messageRepository, never()).findById(any());
        }

        @Test
        @DisplayName("reason=ETC이고 reasonText가 공백만 있어도 REASON_TEXT_REQUIRED다")
        void blankReasonTextIsRejected() {
            assertThatThrownBy(() -> service.create(REPORTER_ID, messageReportRequest(ReportReason.ETC, "   ")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ReportErrorCode.REASON_TEXT_REQUIRED);
        }

        @Test
        @DisplayName("reason=ETC이고 reasonText가 있으면 통과한다")
        void etcWithReasonTextPasses() {
            stubHappyPath();

            Report saved = Report.createMessageReport(
                    reporter, reportedUser, reportedMessage, ReportReason.ETC, "기타 사유입니다");
            ReflectionTestUtils.setField(saved, "id", 902L);
            given(reportRepository.save(any(Report.class))).willReturn(saved);

            ReportCreateResponse result = service.create(REPORTER_ID, messageReportRequest(ReportReason.ETC, "기타 사유입니다"));

            assertThat(result.id()).isEqualTo(902L);
        }
    }
}
