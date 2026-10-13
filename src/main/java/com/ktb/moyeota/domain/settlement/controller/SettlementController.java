package com.ktb.moyeota.domain.settlement.controller;

import com.ktb.moyeota.domain.settlement.dto.MeterAnalysisResponse;
import com.ktb.moyeota.domain.settlement.dto.MyTransferResponse;
import com.ktb.moyeota.domain.settlement.dto.SettlementAmountRequest;
import com.ktb.moyeota.domain.settlement.dto.SettlementAmountResponse;
import com.ktb.moyeota.domain.settlement.dto.SettlementOverviewResponse;
import com.ktb.moyeota.domain.settlement.dto.TransferProofResponse;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.settlement.model.MyTransfer;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.settlement.model.SettlementOverview;
import com.ktb.moyeota.domain.settlement.model.TransferProof;
import com.ktb.moyeota.domain.settlement.service.SettlementOverviewService;
import com.ktb.moyeota.domain.settlement.service.SettlementService;
import com.ktb.moyeota.domain.settlement.service.TransferProofService;
import com.ktb.moyeota.domain.settlement.success.SettlementSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementOverviewService settlementOverviewService;
    private final TransferProofService transferProofService;

    @PutMapping("/settlements/{settlement_id}/amount")
    public ApiResponse<SettlementAmountResponse> inputAmount(
            @AuthUser Long userId,
            @PathVariable("settlement_id") Long settlementId,
            @Valid @RequestBody SettlementAmountRequest request) {
        RequestedSettlement requested = settlementService.requestManually(userId, settlementId, request.totalAmountValue());
        return ApiResponse.of(SettlementSuccessCode.AMOUNT_ENTERED, toResponse(requested));
    }

    @GetMapping("/settlements/{settlement_id}")
    public ApiResponse<SettlementOverviewResponse> get(
            @AuthUser Long userId, @PathVariable("settlement_id") Long settlementId) {
        SettlementOverview overview = settlementOverviewService.find(userId, settlementId);
        return ApiResponse.of(SettlementSuccessCode.SETTLEMENT_FOUND, toResponse(overview));
    }

    @PostMapping("/settlements/{settlement_id}/completion")
    public ResponseEntity<Void> complete(@AuthUser Long userId, @PathVariable("settlement_id") Long settlementId) {
        settlementService.complete(userId, settlementId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settlements/{settlement_id}/analysis-status")
    public ApiResponse<MeterAnalysisResponse> getMeterAnalysis(
            @AuthUser Long userId, @PathVariable("settlement_id") Long settlementId) {
        MeterAnalysis analysis = settlementService.findMeterAnalysis(userId, settlementId);
        return ApiResponse.of(SettlementSuccessCode.METER_ANALYSIS_FOUND, toResponse(analysis));
    }

    @GetMapping("/settlements/{settlement_id}/participants/me")
    public ApiResponse<MyTransferResponse> getMyTransfer(
            @AuthUser Long userId, @PathVariable("settlement_id") Long settlementId) {
        MyTransfer transfer = transferProofService.findMine(userId, settlementId);
        return ApiResponse.of(SettlementSuccessCode.MY_TRANSFER_FOUND, toResponse(transfer));
    }

    @GetMapping("/settlements/{settlement_id}/participants/{user_id}/transfer-proof")
    public ApiResponse<TransferProofResponse> getTransferProof(
            @AuthUser Long userId,
            @PathVariable("settlement_id") Long settlementId,
            @PathVariable("user_id") Long targetUserId) {
        TransferProof proof = transferProofService.find(userId, settlementId, targetUserId);
        return ApiResponse.of(SettlementSuccessCode.TRANSFER_PROOF_FOUND, toResponse(proof));
    }

    private static SettlementAmountResponse toResponse(RequestedSettlement requested) {
        return new SettlementAmountResponse(
                requested.participantCount(),
                requested.amountPerPerson(),
                requested.status().name(),
                requested.meterAnalysisStatus().name());
    }

    private static MeterAnalysisResponse toResponse(MeterAnalysis analysis) {
        return new MeterAnalysisResponse(
                analysis.status().name(),
                analysis.meterAnalysisStatus().name(),
                analysis.totalAmount(),
                analysis.analysisStartedAt(),
                analysis.remainingMeterAttempts());
    }

    private static MyTransferResponse toResponse(MyTransfer transfer) {
        return transfer.charge()
                .map(charge -> new MyTransferResponse(
                        transfer.participantCount(),
                        transfer.status().name(),
                        charge.analysisStatus().name(),
                        charge.amount(),
                        charge.transferredAt(),
                        new MyTransferResponse.Payee(
                                charge.payee().name(), charge.payee().bankName(), charge.payee().accountNo())))
                .orElseGet(() -> new MyTransferResponse(
                        transfer.participantCount(), transfer.status().name(), null, null, null, null));
    }

    private static TransferProofResponse toResponse(TransferProof proof) {
        return new TransferProofResponse(proof.analysisStatus().name(), proof.imageUrl(), proof.transferredAt());
    }

    private static SettlementOverviewResponse toResponse(SettlementOverview overview) {
        return new SettlementOverviewResponse(
                overview.id(),
                overview.status().name(),
                overview.meterAnalysisStatus().name(),
                overview.totalAmount(),
                overview.amountPerPerson(),
                overview.participantCount(),
                overview.completedAt(),
                overview.payers().stream()
                        .map(payer -> new SettlementOverviewResponse.Payer(
                                payer.userId(),
                                payer.name(),
                                payer.profileImageUrl(),
                                payer.amount(),
                                payer.analysisStatus().name(),
                                payer.transferredAt(),
                                payer.hasTransferProof()))
                        .toList());
    }
}
