package com.ktb.moyeota.domain.settlement.entity;

import com.ktb.moyeota.domain.companion.entity.Companion;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlements_companion", columnNames = "companion_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    private static final int METER_ATTEMPT_LIMIT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "companion_id", nullable = false)
    private Companion companion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    private User payee;

    @Column(name = "payee_bank_name", nullable = false, length = 30)
    private String payeeBankName;

    @Column(name = "payee_account_no", nullable = false)
    private byte[] payeeAccountNo;

    @Column(name = "meter_image_url")
    private String meterImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_analysis_status", nullable = false, length = 20)
    private MeterAnalysisStatus meterAnalysisStatus;

    @Column(name = "analysis_trial_count", nullable = false)
    private Integer analysisTrialCount;

    @Column(name = "analysis_started_at")
    private LocalDateTime analysisStartedAt;

    @Column(name = "total_amount")
    private Integer totalAmount;

    @Column(name = "participant_count", nullable = false, columnDefinition = "TINYINT")
    private Integer participantCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private Settlement(Companion companion, User payee, int participantCount) {
        this.companion = companion;
        this.payee = payee;
        this.payeeBankName = payee.getBankName();
        this.payeeAccountNo = payee.getAccountNo();
        this.meterAnalysisStatus = MeterAnalysisStatus.PENDING;
        this.analysisTrialCount = 0;
        this.participantCount = participantCount;
        this.status = SettlementStatus.PENDING;
    }

    public static Settlement open(Companion companion) {
        User payee = companion.getHost();
        if (!payee.hasBankAccount()) {
            throw new IllegalStateException("정산 받을 방장에게 계좌가 없다: " + companion.getId());
        }
        return new Settlement(companion, payee, companion.getCurrentCount());
    }

    public boolean isPaidTo(Long userId) {
        return payee.getId().equals(userId);
    }

    public void submitMeter(String meterImageKey, LocalDateTime startedAt) {
        checkMeterSubmittable();
        this.meterImageUrl = meterImageKey;
        this.meterAnalysisStatus = MeterAnalysisStatus.ANALYZING;
        this.analysisTrialCount++;
        this.analysisStartedAt = startedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    public void checkMeterSubmittable() {
        if (status != SettlementStatus.PENDING) {
            throw new BusinessException(SettlementErrorCode.ALREADY_REQUESTED);
        }
        if (meterAnalysisStatus == MeterAnalysisStatus.ANALYZING) {
            throw new BusinessException(SettlementErrorCode.METER_ANALYSIS_IN_PROGRESS);
        }
        if (analysisTrialCount >= METER_ATTEMPT_LIMIT) {
            throw new BusinessException(SettlementErrorCode.RETRY_LIMIT_EXCEEDED);
        }
    }

    public boolean isAnalyzingMeter(LocalDateTime startedAt) {
        return meterAnalysisStatus == MeterAnalysisStatus.ANALYZING && startedAt.equals(analysisStartedAt);
    }

    public void failMeter() {
        requireAnalyzingMeter();
        this.meterAnalysisStatus = MeterAnalysisStatus.FAILED;
    }

    public void failMeterBySystem() {
        requireAnalyzingMeter();
        this.meterAnalysisStatus = MeterAnalysisStatus.FAILED;
        this.analysisTrialCount--;
    }

    public void requestByMeter(int totalAmount) {
        requireAnalyzingMeter();
        this.totalAmount = totalAmount;
        this.meterAnalysisStatus = MeterAnalysisStatus.PASSED;
        this.status = SettlementStatus.REQUESTED;
    }

    private void requireAnalyzingMeter() {
        if (status != SettlementStatus.PENDING || meterAnalysisStatus != MeterAnalysisStatus.ANALYZING) {
            throw new IllegalStateException("미터기 분석 중이 아닌 정산이다: " + id);
        }
    }

    public int remainingMeterAttempts() {
        return Math.max(0, METER_ATTEMPT_LIMIT - analysisTrialCount);
    }

    public void requestManually(int totalAmount) {
        if (status != SettlementStatus.PENDING) {
            throw new BusinessException(SettlementErrorCode.ALREADY_REQUESTED);
        }
        this.totalAmount = totalAmount;
        this.meterAnalysisStatus = MeterAnalysisStatus.MANUAL;
        this.status = SettlementStatus.REQUESTED;
    }

    public void complete() {
        if (status != SettlementStatus.REQUESTED) {
            throw new BusinessException(SettlementErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = SettlementStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public int amountPerPerson() {
        if (totalAmount == null) {
            throw new IllegalStateException("금액이 정해지지 않은 정산이다: " + id);
        }
        return totalAmount / participantCount;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
