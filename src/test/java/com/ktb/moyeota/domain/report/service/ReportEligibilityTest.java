package com.ktb.moyeota.domain.report.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.ReportFixture.messageReport;
import static com.ktb.moyeota.fixture.ReportFixture.userReport;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.report.dto.ReportCreateResponse;
import com.ktb.moyeota.domain.report.entity.MessageReport;
import com.ktb.moyeota.domain.report.entity.UserReport;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ReportService.class, ReportEligibilityTest.FixedClock.class})
class ReportEligibilityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 12, 0);

    @Autowired
    private ReportService reportService;

    @Autowired
    private TestEntityManager entityManager;

    private User reporter;
    private User reportedUser;
    private Companion pot;
    private Message reportedMessage;

    @BeforeEach
    void setUp() {
        reporter = entityManager.persist(user("신고자"));
        reportedUser = entityManager.persist(user("상대방"));
        pot = entityManager.persist(taxiPot(reportedUser, RECRUITING, 2));
        entityManager.persist(participant(pot, reportedUser, PENDING));
        ChatRoom chatRoom = entityManager.persist(ChatRoom.create(pot));
        reportedMessage = entityManager.persistAndFlush(
                Message.createGeneralMessage(chatRoom, reportedUser, "1", "부적절한 내용"));
    }

    @Nested
    @DisplayName("메시지 신고 자격")
    class MessageReportCase {

        @Test
        @DisplayName("운행을 마친 지 72시간이 안 됐으면 신고할 수 있다")
        void createsWithinPeriodAfterRide() {
            entityManager.persistAndFlush(participant(pot, reporter, COMPLETED, NOW.minusHours(72).plusMinutes(1)));

            ReportCreateResponse result =
                    reportService.create(reporter.getId(), messageReport(reportedUser, reportedMessage));

            assertThat(entityManager.find(MessageReport.class, result.id())).isNotNull();
        }

        @Test
        @DisplayName("운행을 마친 지 72시간이 지났으면 REPORT_PERIOD_EXPIRED다")
        void expiredAfterPeriod() {
            entityManager.persistAndFlush(participant(pot, reporter, COMPLETED, NOW.minusHours(72)));

            assertErrorCode(() -> reportService.create(reporter.getId(), messageReport(reportedUser, reportedMessage)),
                    ReportErrorCode.REPORT_PERIOD_EXPIRED);
        }

        @Test
        @DisplayName("신고 대상이 메시지를 보낸 사람이 아니면 REPORT_TARGET_INVALID다")
        void notSender() {
            entityManager.persistAndFlush(participant(pot, reporter, PENDING));
            User other = entityManager.persist(user("다른사람"));
            entityManager.persistAndFlush(participant(pot, other, PENDING));

            assertErrorCode(() -> reportService.create(reporter.getId(), messageReport(other, reportedMessage)),
                    ReportErrorCode.REPORT_TARGET_INVALID);
        }

        @Test
        @DisplayName("보낸 사람이 없는 시스템 메시지는 REPORT_TARGET_INVALID다")
        void systemMessage() {
            entityManager.persistAndFlush(participant(pot, reporter, PENDING));
            reportedMessage = entityManager.persistAndFlush(
                    Message.rideEndRequestedSystemMessage(reportedMessage.getChatRoom(), "-1", null));

            assertErrorCode(() -> reportService.create(reporter.getId(), messageReport(reportedUser, reportedMessage)),
                    ReportErrorCode.REPORT_TARGET_INVALID);
        }

        @Test
        @DisplayName("자기 메시지를 신고하면 REPORT_TARGET_INVALID다")
        void ownMessage() {
            entityManager.persistAndFlush(participant(pot, reporter, PENDING));
            reportedMessage = entityManager.persistAndFlush(
                    Message.createGeneralMessage(reportedMessage.getChatRoom(), reporter, "2", "내 메시지"));

            assertErrorCode(() -> reportService.create(reporter.getId(), messageReport(reporter, reportedMessage)),
                    ReportErrorCode.REPORT_TARGET_INVALID);
        }
    }

    @Nested
    @DisplayName("사용자 신고 자격")
    class UserReportCase {

        @Test
        @DisplayName("운행을 마친 지 72시간이 안 됐으면 신고할 수 있다")
        void createsWithinPeriodAfterRide() {
            entityManager.persistAndFlush(participant(pot, reporter, COMPLETED, NOW.minusHours(72).plusMinutes(1)));

            ReportCreateResponse result = reportService.create(reporter.getId(), userReport(reportedUser, pot));

            assertThat(entityManager.find(UserReport.class, result.id())).isNotNull();
        }

        @Test
        @DisplayName("운행을 마친 지 72시간이 지났으면 REPORT_PERIOD_EXPIRED다")
        void expiredAfterPeriod() {
            entityManager.persistAndFlush(participant(pot, reporter, COMPLETED, NOW.minusHours(72)));

            assertErrorCode(() -> reportService.create(reporter.getId(), userReport(reportedUser, pot)),
                    ReportErrorCode.REPORT_PERIOD_EXPIRED);
        }

        @Test
        @DisplayName("중간에 나간 신고자는 REPORT_TARGET_INVALID다")
        void reporterLeft() {
            entityManager.persistAndFlush(participant(pot, reporter, INCOMPLETE, NOW.minusHours(1)));

            assertErrorCode(() -> reportService.create(reporter.getId(), userReport(reportedUser, pot)),
                    ReportErrorCode.REPORT_TARGET_INVALID);
        }

        @Test
        @DisplayName("중간에 나간 사람도 함께 있던 사람이라 신고할 수 있다")
        void reportsLeftParticipant() {
            entityManager.persistAndFlush(participant(pot, reporter, PENDING));
            User leaver = entityManager.persist(user("나간사람"));
            entityManager.persistAndFlush(participant(pot, leaver, INCOMPLETE));

            ReportCreateResponse result = reportService.create(reporter.getId(), userReport(leaver, pot));

            assertThat(entityManager.find(UserReport.class, result.id())).isNotNull();
        }

        @Test
        @DisplayName("팟에 없던 사람을 신고하면 REPORT_TARGET_INVALID다")
        void reportedUserNotInPot() {
            entityManager.persistAndFlush(participant(pot, reporter, PENDING));
            User stranger = entityManager.persistAndFlush(user("모르는사람"));

            assertErrorCode(() -> reportService.create(reporter.getId(), userReport(stranger, pot)),
                    ReportErrorCode.REPORT_TARGET_INVALID);
        }

        @Test
        @DisplayName("자기 자신을 신고하면 REPORT_TARGET_INVALID다")
        void self() {
            entityManager.persistAndFlush(participant(pot, reporter, PENDING));

            assertErrorCode(() -> reportService.create(reporter.getId(), userReport(reporter, pot)),
                    ReportErrorCode.REPORT_TARGET_INVALID);
        }
    }

    private static void assertErrorCode(ThrowingCallable call, ReportErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
