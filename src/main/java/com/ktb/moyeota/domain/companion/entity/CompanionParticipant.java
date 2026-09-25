package com.ktb.moyeota.domain.companion.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "companion_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participants_companion_user", columnNames = {"companion_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_status", nullable = false, length = 20)
    private ParticipantOutcome outcomeStatus;

    private CompanionParticipant(Companion companion, User user, LocalDateTime joinedAt) {
        this.companion = companion;
        this.user = user;
        this.joinedAt = joinedAt;
        this.outcomeStatus = ParticipantOutcome.PENDING;
    }

    public static CompanionParticipant join(Companion companion, User user, LocalDateTime joinedAt) {
        return new CompanionParticipant(companion, user, joinedAt);
    }
}
