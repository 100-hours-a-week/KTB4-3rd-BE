package com.ktb.moyeota.domain.chat.entity;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "companion_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participants_companion_user", columnNames = {"companion_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private Message message;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_status", nullable = false, length = 20)
    private OutcomeStatus outcomeStatus;

    private CompanionParticipant(Companion companion, User user){
        this.companion = companion;
        this.user = user;
        this.outcomeStatus = OutcomeStatus.PENDING;
    }

    @PrePersist
    private void prePersist() {
        if (this.joinedAt == null) {
            this.joinedAt = LocalDateTime.now();
        }
    }

    public static CompanionParticipant join(Companion companion, User user) {
        return new CompanionParticipant(companion, user);
    }

    public static CompanionParticipant join(Companion companion, User user, CompanionParticipant previous) {
        if (previous == null) {
            return join(companion, user);
        }
        previous.rejoin();
        return previous;
    }

    public void leave() {
        if (outcomeStatus != OutcomeStatus.PENDING) {
            throw new IllegalStateException("진행 중이 아닌 참여는 나갈 수 없다: " + id);
        }
        this.outcomeStatus = OutcomeStatus.INCOMPLETE;
        this.leftAt = LocalDateTime.now();
    }

    private void rejoin() {
        if (outcomeStatus != OutcomeStatus.INCOMPLETE) {
            throw new IllegalStateException("나간 참여만 다시 참여할 수 있다: " + id);
        }
        this.outcomeStatus = OutcomeStatus.PENDING;
        this.leftAt = null;
        this.joinedAt = LocalDateTime.now();
    }
}
