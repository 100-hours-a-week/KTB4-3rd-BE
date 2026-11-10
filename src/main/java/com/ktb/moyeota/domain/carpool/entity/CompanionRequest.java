package com.ktb.moyeota.domain.carpool.entity;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
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
@Table(
        name = "companion_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_requests_pending", columnNames = {"companion_id", "pending_requester_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(nullable = false, length = 200)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanionRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "pending_requester_id", insertable = false, updatable = false,
            columnDefinition = "BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN requester_id END)")
    private Long pendingRequesterId;

    private CompanionRequest(Companion companion, User requester, String content) {
        this.companion = companion;
        this.requester = requester;
        this.content = content;
        this.status = CompanionRequestStatus.PENDING;
    }

    public static CompanionRequest send(Companion carpool, User requester, String content) {
        return new CompanionRequest(carpool, requester, content);
    }

    public void accept() {
        handle(CompanionRequestStatus.ACCEPTED);
    }

    public void reject() {
        handle(CompanionRequestStatus.REJECTED);
    }

    private void handle(CompanionRequestStatus result) {
        if (status != CompanionRequestStatus.PENDING) {
            throw new BusinessException(CarpoolErrorCode.REQUEST_ALREADY_HANDLED);
        }
        this.status = result;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
