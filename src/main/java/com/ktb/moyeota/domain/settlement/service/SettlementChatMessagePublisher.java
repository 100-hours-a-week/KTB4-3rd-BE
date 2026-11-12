package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.domain.settlement.event.SettlementCompletedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementConfirmRequestedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferDoneEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferExhaustedEvent;
import com.ktb.moyeota.domain.settlement.event.SettlementTransferRequestedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "direct",
        matchIfMissing = true)
public class SettlementChatMessagePublisher {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;
    private final SettlementSummaryReader settlementSummaryReader;
    private final UserRepository userRepository;

    @EventListener
    public void onTransferRequested(SettlementTransferRequestedEvent event) {
        openChatRoom(event.companionId()).ifPresent(chatRoom -> chatSystemMessageService.requestTransfer(
                chatRoom, settlementSummaryReader.findByCompanionId(event.companionId()).orElseThrow()));
    }

    @EventListener
    public void onCompleted(SettlementCompletedEvent event) {
        openChatRoom(event.companionId()).ifPresent(chatSystemMessageService::completeSettlement);
    }

    @EventListener
    public void onTransferExhausted(SettlementTransferExhaustedEvent event) {
        openChatRoom(event.companionId()).ifPresent(chatSystemMessageService::notifyTransferUnverified);
    }

    @EventListener
    public void onTransferDone(SettlementTransferDoneEvent event) {
        openChatRoom(event.companionId()).ifPresent(chatRoom -> chatSystemMessageService.transferDone(
                chatRoom, userRepository.findById(event.userId()).orElseThrow(),
                settlementSummaryReader.findTransferAmounts(event.companionId()).get(event.userId())));
    }

    @EventListener
    public void onConfirmRequested(SettlementConfirmRequestedEvent event) {
        openChatRoom(event.companionId()).ifPresent(chatSystemMessageService::requestSettlementConfirm);
    }

    private Optional<ChatRoom> openChatRoom(Long companionId) {
        return chatRoomRepository.findByCompanionId(companionId)
                .filter(chatRoom -> chatRoom.getClosedAt() == null);
    }
}
