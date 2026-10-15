package com.ktb.moyeota.domain.settlement.entity;

import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
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

    private static final int TRANSFER_ATTEMPT_LIMIT = 2;

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

    public void submitTransfer(String transferImageKey) {
        checkTransferSubmittable();
        this.transferImageUrl = transferImageKey;
        this.analysisStatus = TransferAnalysisStatus.ANALYZING;
        this.transferImageAnalysisTrialCount++;
    }

    public void checkTransferSubmittable() {
        if (analysisStatus == TransferAnalysisStatus.ANALYZING) {
            throw new BusinessException(SettlementErrorCode.TRANSFER_ANALYSIS_IN_PROGRESS);
        }
        if (transferCompleted) {
            throw new BusinessException(SettlementErrorCode.ALREADY_TRANSFERRED);
        }
        if (transferImageAnalysisTrialCount >= TRANSFER_ATTEMPT_LIMIT) {
            throw new BusinessException(SettlementErrorCode.TRANSFER_RETRY_LIMIT_EXCEEDED);
        }
    }

    public boolean isAnalyzingTransfer(int attempt) {
        return analysisStatus == TransferAnalysisStatus.ANALYZING && transferImageAnalysisTrialCount == attempt;
    }

    public void recordTransferAmount(int readAmount) {
        requireAnalyzingTransfer();
        this.analysisStatus = TransferAnalysisStatus.PASSED;
        if (amount == readAmount) {
            confirmTransfer();
        }
    }

    public void failTransfer() {
        requireAnalyzingTransfer();
        this.analysisStatus = TransferAnalysisStatus.FAILED;
    }

    public int remainingTransferAttempts() {
        return Math.max(0, TRANSFER_ATTEMPT_LIMIT - transferImageAnalysisTrialCount);
    }

    private void requireAnalyzingTransfer() {
        if (analysisStatus != TransferAnalysisStatus.ANALYZING) {
            throw new IllegalStateException("송금 인증 분석 중이 아닌 청구다: " + id);
        }
    }
}
