package com.ktb.moyeota.global.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(
        name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_outbox_events_status_id", columnList = "status, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int LAST_ERROR_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", length = 100)
    private String messageKey;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private OutboxEvent(
            String topic,
            String messageKey,
            String eventId,
            String eventType,
            int schemaVersion,
            LocalDateTime occurredAt,
            String payload,
            String traceId,
            LocalDateTime now) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.traceId = traceId;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = now;
    }

    public static OutboxEvent create(
            String topic,
            String messageKey,
            String eventId,
            String eventType,
            int schemaVersion,
            LocalDateTime occurredAt,
            String payload,
            String traceId,
            LocalDateTime now) {
        requireText(topic, "topic");
        requireText(eventId, "event_id");
        requireText(eventType, "event_type");
        requireText(payload, "payload");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("아웃박스에는 1 이상의 schema_version 이 필요합니다");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("아웃박스에는 occurred_at 이 필요합니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("아웃박스에는 created_at 이 필요합니다");
        }
        return new OutboxEvent(
                topic, messageKey, eventId, eventType, schemaVersion, occurredAt, payload, traceId, now);
    }

    public void markPublished(LocalDateTime now) {
        requirePending();
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void recordFailure(String error) {
        requirePending();
        this.attempts++;
        this.lastError = truncate(error);
    }

    private void requirePending() {
        if (status != OutboxStatus.PENDING) {
            throw new IllegalStateException("발행 대기(PENDING) 중인 이벤트만 결과를 기록할 수 있습니다. status=" + status);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("아웃박스에는 " + name + " 이 필요합니다");
        }
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= LAST_ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
