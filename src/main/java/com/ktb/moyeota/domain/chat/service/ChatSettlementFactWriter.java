package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedData;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
import com.ktb.moyeota.domain.settlement.service.SettlementSummaryReader;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatSettlementFactWriter {

    private final ChatRoomRepository chatRoomRepository;
    private final SettlementSummaryReader settlementSummaryReader;
    private final SystemMessageWriter systemMessageWriter;
    private final UserRepository userRepository;

    @Transactional
    public boolean write(String eventId, String eventType, SettlementChangedData data) {
        Optional<ChatRoom> openChatRoom = chatRoomRepository.findByCompanionId(data.companionId())
                .filter(chatRoom -> chatRoom.getClosedAt() == null);
        if (openChatRoom.isEmpty()) {
            return false;
        }
        ChatRoom chatRoom = openChatRoom.get();
        switch (eventType) {
            case SettlementChangedMessages.TRANSFER_REQUESTED -> systemMessageWriter.requestTransfer(
                    chatRoom, summary(data), SystemMessageKeys.of(eventId, MessageType.SYSTEM_TRANSFER_REQUESTED));
            case SettlementChangedMessages.TRANSFER_EXHAUSTED -> systemMessageWriter.notifyTransferUnverified(
                    chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_TRANSFER_UNVERIFIED));
            case SettlementChangedMessages.TRANSFER_DONE -> systemMessageWriter.transferDone(
                    chatRoom, transferrer(eventId, data), transferAmount(data),
                    SystemMessageKeys.of(eventId, MessageType.SYSTEM_TRANSFER_DONE));
            case SettlementChangedMessages.CONFIRM_REQUESTED -> systemMessageWriter.requestSettlementConfirm(
                    chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_SETTLEMENT_CONFIRM_REQUESTED));
            case SettlementChangedMessages.COMPLETED -> systemMessageWriter.completeSettlement(
                    chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_SETTLEMENT_COMPLETED));
            default -> throw new InvalidEventException("채팅이 처리하지 않는 정산 바뀜 종류입니다. event_id="
                    + eventId + " event_type=" + eventType);
        }
        return true;
    }

    private User transferrer(String eventId, SettlementChangedData data) {
        if (data.userId() == null) {
            throw new InvalidEventException("송금 완료 메시지에 user_id 가 있어야 합니다. event_id=" + eventId);
        }
        return userRepository.findById(data.userId())
                .orElseThrow(() -> new IllegalStateException("송금한 사용자가 없다. user_id=" + data.userId()));
    }

    private int transferAmount(SettlementChangedData data) {
        Integer amount = settlementSummaryReader.findTransferAmounts(data.companionId()).get(data.userId());
        if (amount == null) {
            throw new IllegalStateException("송금한 사람의 청구가 없다. settlement_id=" + data.settlementId()
                    + " user_id=" + data.userId());
        }
        return amount;
    }

    private MessageItem.Settlement summary(SettlementChangedData data) {
        return settlementSummaryReader.findByCompanionId(data.companionId())
                .orElseThrow(() -> new IllegalStateException(
                        "금액이 정해진 정산이 없다. settlement_id=" + data.settlementId()));
    }
}
