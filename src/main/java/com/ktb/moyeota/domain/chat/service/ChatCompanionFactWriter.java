package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatCompanionFactWriter {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SystemMessageWriter systemMessageWriter;

    @Transactional
    public boolean write(String eventId, String eventType, CompanionChangedData data) {
        Optional<ChatRoom> openChatRoom = chatRoomRepository.findByCompanionId(data.companionId())
                .filter(chatRoom -> chatRoom.getClosedAt() == null);
        if (openChatRoom.isEmpty()) {
            return false;
        }
        ChatRoom chatRoom = openChatRoom.get();
        switch (eventType) {
            case CompanionChangedMessages.MATCHED, CompanionChangedMessages.PARTICIPANT_JOINED ->
                    systemMessageWriter.enter(chatRoom, participant(eventId, data),
                            SystemMessageKeys.of(eventId, MessageType.SYSTEM_JOIN));
            case CompanionChangedMessages.PARTICIPANT_LEFT ->
                    systemMessageWriter.leave(chatRoom, participant(eventId, data),
                            SystemMessageKeys.of(eventId, MessageType.SYSTEM_LEAVE));
            case CompanionChangedMessages.RIDE_STARTED -> systemMessageWriter.rideStarted(
                    chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_RIDE_STARTED));
            case CompanionChangedMessages.RIDE_ENDED -> writeRideEnded(eventId, data, chatRoom);
            default -> throw new InvalidEventException("채팅이 처리하지 않는 동행 바뀜 종류입니다. event_id="
                    + eventId + " event_type=" + eventType);
        }
        return true;
    }

    private void writeRideEnded(String eventId, CompanionChangedData data, ChatRoom chatRoom) {
        systemMessageWriter.rideEnded(chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_RIDE_ENDED));
        if (data.kind() == CompanionKind.TAXI_POT) {
            systemMessageWriter.requestMeter(
                    chatRoom, SystemMessageKeys.of(eventId, MessageType.SYSTEM_METER_REQUESTED));
        }
    }

    private User participant(String eventId, CompanionChangedData data) {
        if (data.userId() == null) {
            throw new InvalidEventException("입장 · 퇴장 메시지에 user_id 가 없습니다. event_id=" + eventId);
        }
        return userRepository.findById(data.userId())
                .orElseThrow(() -> new InvalidEventException(
                        "입장 · 퇴장한 사용자가 없습니다. event_id=" + eventId + " user_id=" + data.userId()));
    }
}
