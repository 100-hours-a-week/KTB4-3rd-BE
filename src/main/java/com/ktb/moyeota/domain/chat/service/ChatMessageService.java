package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageCursor;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int PAGE_SIZE = 20;

    private final MessageRepository messageRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final MessageCursorCodec messageCursorCodec;

    @Transactional(readOnly = true)
    public MessageListResponse findMessages(Long userId, Long roomId, String cursor) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        boolean isFirstPage = (cursor == null || cursor.isBlank());
        MessageCursor decoded = messageCursorCodec.decode(cursor);
        Long startFromId = decoded.lastId();

        if (isFirstPage) {
            Message lastRead = participant.getMessage();
            if (lastRead != null) {
                startFromId = lastRead.getId() + 1;
            }
        }

        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1);
        List<Message> fetched = messageRepository.findByChatRoomIdWithSender(roomId, startFromId, pageable);

        boolean hasNext = fetched.size() > PAGE_SIZE;
        List<Message> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;

        String nextCursor = hasNext
                ? messageCursorCodec.encode(new MessageCursor(page.get(page.size() - 1).getId()))
                : null;

        List<MessageItem> items = page.stream()
                .map(MessageItem::from)
                .toList();

        return new MessageListResponse(items, nextCursor);
    }
}
