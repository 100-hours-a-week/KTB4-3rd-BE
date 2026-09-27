package com.ktb.moyeota.domain.rating.entity;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "companion_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ratings_companion_rater_ratee", columnNames = {"companion_id", "rater_id", "ratee_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ratee_id", nullable = false)
    private User ratee;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer score;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private CompanionRating(Companion companion, User rater, User ratee, Integer score) {
        this.companion = companion;
        this.rater = rater;
        this.ratee = ratee;
        this.score = score;
    }

    public static CompanionRating of(Companion companion, User rater, User ratee, Integer score) {
        return new CompanionRating(companion, rater, ratee, score);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
