package com.ktb.moyeota.domain.settlement.entity;

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
        name = "settlement_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_participants_user", columnNames = {"settlement_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "is_forged")
    private Boolean forged;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private TransferAnalysisStatus analysisStatus;

    @Column(name = "transfer_image_url")
    private String transferImageUrl;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Column(name = "transfer_completed", nullable = false)
    private boolean transferCompleted;

    @Column(name = "transfer_image_analysis_trial_count", nullable = false)
    private Integer transferImageAnalysisTrialCount;

    private SettlementParticipant(Settlement settlement, User user, int amount) {
        this.settlement = settlement;
        this.user = user;
        this.amount = amount;
        this.analysisStatus = TransferAnalysisStatus.PENDING;
        this.transferCompleted = false;
        this.transferImageAnalysisTrialCount = 0;
    }

    public static SettlementParticipant charge(Settlement settlement, User user, int amount) {
        return new SettlementParticipant(settlement, user, amount);
    }

    public void confirmTransfer() {
        this.transferCompleted = true;
    }
}
