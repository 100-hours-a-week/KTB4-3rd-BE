package com.ktb.moyeota.domain.report.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.report.dto.ReportCreateRequest;
import com.ktb.moyeota.domain.report.dto.ReportCreateResponse;
import com.ktb.moyeota.domain.report.entity.MessageReport;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.mongodb.test.autoconfigure.AutoConfigureDataMongo;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataJpaTest(properties = "moyeota.chat.message-store=mongo")
@AutoConfigureDataMongo
@Import({ReportService.class, MongoReportedMessageResolver.class, ChatMessageRepository.class,
        TestClockConfig.class, MongoTestContainerConfig.class})
class MongoReportedMessageTest {

    private static final Instant NOW = Instant.parse("2026-11-23T01:00:00Z");

    @Autowired
    private ReportService reportService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User reporter;
    private User reportedUser;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(ChatMessage.class);
        ChatMongoScripts.applyIndexes();
        reporter = entityManager.persist(user("신고자"));
        reportedUser = entityManager.persist(user("상대방"));
        Companion pot = entityManager.persist(taxiPot(reportedUser, RECRUITING, 2));
        entityManager.persist(participant(pot, reportedUser, PENDING));
        entityManager.persist(participant(pot, reporter, PENDING));
        chatRoom = entityManager.persistAndFlush(ChatRoom.create(pot));
    }

    @Test
    @DisplayName("MongoDB 메시지를 UUID 문자열로 신고하면 FK 없이 UUID 로 저장된다")
    void reportsChatMessageByUuid() {
        ChatMessage message = textFrom(reportedUser);

        ReportCreateResponse result = reportService.create(reporter.getId(), request(message.getId().toString()));

        MessageReport saved = entityManager.find(MessageReport.class, result.id());
        assertThat(saved.getReportedChatMessageId()).isEqualTo(message.getId().toString());
        assertThat(saved.getReportedMessage()).isNull();
    }

    @Test
    @DisplayName("없는 메시지, UUID 가 아닌 id 는 REPORT_TARGET_INVALID 다")
    void rejectsUnknownOrMalformedId() {
        assertErrorCode(() -> reportService.create(reporter.getId(), request(UUID.randomUUID().toString())),
                ReportErrorCode.REPORT_TARGET_INVALID);
        assertErrorCode(() -> reportService.create(reporter.getId(), request("1441")),
                ReportErrorCode.REPORT_TARGET_INVALID);
    }

    @Test
    @DisplayName("시스템 메시지나 신고 대상이 보내지 않은 메시지는 REPORT_TARGET_INVALID 다")
    void rejectsSystemMessageOrOtherSender() {
        ChatMessage system = chatMessageRepository.append(ChatMessage.system(chatRoom.getId(), MessageType.SYSTEM_JOIN,
                reportedUser.getId(), UUID.randomUUID().toString(), null, NOW));
        ChatMessage fromReporter = textFrom(reporter);

        assertErrorCode(() -> reportService.create(reporter.getId(), request(system.getId().toString())),
                ReportErrorCode.REPORT_TARGET_INVALID);
        assertErrorCode(() -> reportService.create(reporter.getId(), request(fromReporter.getId().toString())),
                ReportErrorCode.REPORT_TARGET_INVALID);
    }

    @Test
    @DisplayName("같은 메시지를 다시 신고하면 DUPLICATE_MESSAGE_REPORT 다")
    void rejectsDuplicate() {
        ChatMessage message = textFrom(reportedUser);
        reportService.create(reporter.getId(), request(message.getId().toString()));

        assertErrorCode(() -> reportService.create(reporter.getId(), request(message.getId().toString())),
                ReportErrorCode.DUPLICATE_MESSAGE_REPORT);
    }

    private ChatMessage textFrom(User sender) {
        return chatMessageRepository.append(ChatMessage.text(chatRoom.getId(), sender.getId(),
                UUID.randomUUID().toString(), "부적절한 내용", NOW));
    }

    private ReportCreateRequest request(String reportedMessageId) {
        return new ReportCreateRequest(reportedUser.getId(), reportedMessageId, null, ReportReason.ABUSE, null);
    }

    private static void assertErrorCode(ThrowingCallable call, ReportErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
