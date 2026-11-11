package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedData;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
import com.ktb.moyeota.domain.settlement.service.SettlementSummaryReader;
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
            case SettlementChangedMessages.COMPLETED -> systemMessageWriter.completeSettlement(
                    chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_SETTLEMENT_COMPLETED));
            default -> throw new InvalidEventException("채팅이 처리하지 않는 정산 바뀜 종류입니다. event_id="
                    + eventId + " event_type=" + eventType);
        }
        return true;
    }

    private MessageItem.Settlement summary(SettlementChangedData data) {
        return settlementSummaryReader.findByCompanionId(data.companionId())
                .orElseThrow(() -> new IllegalStateException(
                        "금액이 정해진 정산이 없다. settlement_id=" + data.settlementId()));
    }
}
