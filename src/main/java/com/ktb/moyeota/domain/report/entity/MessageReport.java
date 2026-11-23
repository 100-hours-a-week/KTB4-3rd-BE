package com.ktb.moyeota.domain.report.entity;

import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reports_message", columnNames = {"reporter_id", "reported_message_id"}),
                @UniqueConstraint(name = "uk_reports_chat_message",
                        columnNames = {"reporter_id", "reported_chat_message_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_message_id")
    private Message reportedMessage;

    @Column(name = "reported_chat_message_id", length = 36)
    private String reportedChatMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private ReportReason reason;

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MessageReport(User reporter, User reportedUser, Message reportedMessage, String reportedChatMessageId,
                           ReportReason reason, String reasonText) {
        this.reporter = reporter;
        this.reportedUser = reportedUser;
        this.reportedMessage = reportedMessage;
        this.reportedChatMessageId = reportedChatMessageId;
        this.reason = reason;
        this.reasonText = reasonText;
    }

    public static MessageReport create(User reporter, User reportedUser, Message reportedMessage,
                                        ReportReason reason, String reasonText) {
        return new MessageReport(reporter, reportedUser, reportedMessage, null, reason, reasonText);
    }

    public static MessageReport createForChatMessage(User reporter, User reportedUser, String reportedChatMessageId,
                                                     ReportReason reason, String reasonText) {
        return new MessageReport(reporter, reportedUser, null, reportedChatMessageId, reason, reasonText);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
