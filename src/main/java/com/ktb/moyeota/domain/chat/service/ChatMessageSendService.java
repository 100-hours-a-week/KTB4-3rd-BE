package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private final ChatRoomRepository chatRoomRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public Optional<MessageItem> send(Long userId, Long roomId, ChatMessageSendRequest request) {
        Optional<CompanionParticipant> participant =
                companionParticipantRepository.findActiveByChatRoomIdAndUserId(roomId, userId);
        if (participant.isEmpty()) {
            return Optional.empty();
        }

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND));
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        Message saved;
        try {
            saved = messageRepository.save(
                    Message.createGeneralMessage(chatRoom, sender, request.clientMessageId(), request.content()));
            chatRoom.updateLastMessageId(saved.getId());
        } catch (DataIntegrityViolationException e) {
            saved = messageRepository.findByChatRoomIdAndClientMessageId(roomId, request.clientMessageId())
                    .orElseThrow(() -> e);
        }

        return Optional.of(MessageItem.from(saved));
    }
}
