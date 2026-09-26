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
@Table(name = "companion_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @ManyToOne(fetch = FetchType.LAZY)
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

    public void leave() {
        this.leftAt = LocalDateTime.now();
    }

}
