package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Sender;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mongo")
public class MongoChatMessageSendService implements ChatMessageWriter {

    private final CompanionParticipantRepository companionParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ImageUrlResolver imageUrlResolver;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public Optional<ChatMessageItem> send(Long userId, Long roomId, ChatMessageSendRequest request) {
        Optional<Sender> sender = transactionTemplate.execute(status -> senderOf(userId, roomId));
        if (sender.isEmpty()) {
            return Optional.empty();
        }
        ChatMessage saved = chatMessageRepository.append(
                ChatMessage.text(roomId, userId, request.clientMessageId(), request.content(), clock.instant()));
        return Optional.of(ChatMessageItem.text(saved, sender.get(), clock.getZone()));
    }

    private Optional<Sender> senderOf(Long userId, Long roomId) {
        if (companionParticipantRepository.findActiveByChatRoomIdAndUserId(roomId, userId).isEmpty()) {
            return Optional.empty();
        }
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return Optional.of(MessageItem.senderOf(
                user, chatRoom.getCompanion().getKind(), imageUrlResolver.toUrl(user.getProfileImageUrl())));
    }
}
