package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.settlement.repository.SettlementParticipantRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRiderRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementRiderRepository settlementRiderRepository;
    private final SettlementParticipantRepository settlementParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final NotificationCommandService notificationCommandService;

    @Transactional
    public RequestedSettlement requestManually(Long userId, Long settlementId, int totalAmount) {
        Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        List<CompanionParticipant> riders = settlementRiderRepository.findRiders(settlement.getCompanion().getId());
        validateHost(settlement, riders, userId);

        settlement.requestManually(totalAmount);
        chargeRiders(settlement, riders);
        notifyRiders(settlement, riders);
        return new RequestedSettlement(settlement.getParticipantCount(), settlement.amountPerPerson(),
                settlement.getStatus(), settlement.getMeterAnalysisStatus());
    }

    @Transactional
    public void complete(Long userId, Long settlementId) {
        Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        Long companionId = settlement.getCompanion().getId();
        List<CompanionParticipant> riders = settlementRiderRepository.findRiders(companionId);
        validateHost(settlement, riders, userId);

        settlement.complete();
        settlementParticipantRepository.findAllBySettlementId(settlementId)
                .forEach(SettlementParticipant::confirmTransfer);
        riders.stream()
                .filter(rider -> rider.getOutcomeStatus() == OutcomeStatus.PENDING)
                .forEach(CompanionParticipant::complete);
        chatRoomRepository.findByCompanionId(companionId)
                .filter(chatRoom -> chatRoom.getClosedAt() == null)
                .ifPresent(ChatRoom::close);
    }

    @Transactional(readOnly = true)
    public MeterAnalysis findMeterAnalysis(Long userId, Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(found -> settlementRiderRepository.existsRider(found.getCompanion().getId(), userId))
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        return new MeterAnalysis(settlement.getStatus(), settlement.getMeterAnalysisStatus(),
                settlement.getTotalAmount(), settlement.getAnalysisStartedAt());
    }

    private void chargeRiders(Settlement settlement, List<CompanionParticipant> riders) {
        int amountPerPerson = settlement.amountPerPerson();
        settlementParticipantRepository.saveAll(riders.stream()
                .filter(rider -> !settlement.isPaidTo(rider.getUser().getId()))
                .map(rider -> SettlementParticipant.charge(settlement, rider.getUser(), amountPerPerson))
                .toList());
    }

    private void notifyRiders(Settlement settlement, List<CompanionParticipant> riders) {
        Long companionId = settlement.getCompanion().getId();
        ChatRoom chatRoom = chatRoomRepository.findByCompanionId(companionId)
                .orElseThrow(() -> new IllegalStateException("택시팟에 채팅방이 없다: " + companionId));
        riders.stream()
                .map(CompanionParticipant::getUser)
                .filter(rider -> !settlement.isPaidTo(rider.getId()))
                .forEach(rider -> notificationCommandService.createForChatRoom(rider,
                        NotificationType.SETTLEMENT_REQUESTED, settlement.getId(), chatRoom, "정산 요청이 도착했어요"));
    }

    private static void validateHost(Settlement settlement, List<CompanionParticipant> riders, Long userId) {
        if (settlement.isPaidTo(userId)) {
            return;
        }
        boolean rider = riders.stream().anyMatch(participant -> participant.getUser().getId().equals(userId));
        throw new BusinessException(rider ? TaxiPotErrorCode.HOST_ONLY : SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }
}
