package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.MessageCursor;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int PAGE_SIZE = 20;
    private static final int ANCHOR_SIDE_SIZE = 10;

    private final MessageRepository messageRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final MessageCursorCodec messageCursorCodec;
    private final ImageUrlResolver imageUrlResolver;

    private static final String DIRECTION_BEFORE = "before";
    private static final String DIRECTION_AFTER = "after";


    @Transactional(readOnly = true)
    public MessageListResponse findMessages(
            Long userId, Long roomId, String direction, String before, String after) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        String beforeCursor = blankToNull(before);
        String afterCursor = blankToNull(after);
        String dir = blankToNull(direction);

        if (dir == null) {
            if (beforeCursor != null || afterCursor != null) {
                throw new BusinessException(ChatErrorCode.INVALID_DIRECTION);
            }
            return findFirstEntry(roomId, participant.getMessage());
        }
        if (DIRECTION_BEFORE.equals(dir)) {
            Long beforeId = decodeRequiredId(beforeCursor);
            validateEcho(afterCursor);
            return findOlder(roomId, beforeId, afterCursor);
        }
        if (DIRECTION_AFTER.equals(dir)) {
            Long afterId = decodeRequiredId(afterCursor);
            validateEcho(beforeCursor);
            return findNewer(roomId, afterId, beforeCursor);
        }
        throw new BusinessException(ChatErrorCode.INVALID_DIRECTION);
    }

    private MessageListResponse findOlder(Long roomId, Long beforeId, String keptAfterCursor) {
        Chunk older = olderThan(roomId, beforeId, PAGE_SIZE);
        return new MessageListResponse(
                toItems(older.messages()),
                older.hasMore() ? cursorOf(last(older.messages())) : null,
                keptAfterCursor);
    }

    private MessageListResponse findNewer(Long roomId, Long afterId, String keptBeforeCursor) {
        Chunk newer = newerThan(roomId, afterId, PAGE_SIZE);
        return new MessageListResponse(
                toItems(newer.messages()),
                keptBeforeCursor,
                newer.hasMore() ? cursorOf(newer.messages().get(0)) : null);
    }

    private MessageListResponse findFirstEntry(Long roomId, Message lastRead) {
        if (lastRead != null) {
            Chunk unread = newerThan(roomId, lastRead.getId(), ANCHOR_SIDE_SIZE);
            if (!unread.messages().isEmpty()) {
                Chunk read = olderThan(roomId, lastRead.getId() + 1, ANCHOR_SIDE_SIZE);
                List<Message> merged = new ArrayList<>(unread.messages());
                merged.addAll(read.messages());
                return new MessageListResponse(toItems(merged),
                        read.hasMore() && !read.messages().isEmpty() ? cursorOf(last(read.messages())) : null,
                        unread.hasMore() ? cursorOf(unread.messages().get(0)) : null);
            }
        }
        Chunk latest = olderThan(roomId, null, PAGE_SIZE);
        return new MessageListResponse(
                toItems(latest.messages()), latest.hasMore() ? cursorOf(last(latest.messages())) : null, null);
    }

    private Chunk olderThan(Long roomId, Long cursor, int size) {
        List<Message> fetched = messageRepository.findByChatRoomIdWithSender(
                roomId, cursor, PageRequest.of(0, size + 1));
        boolean hasMore = fetched.size() > size;
        return new Chunk(hasMore ? fetched.subList(0, size) : fetched, hasMore);
    }

    private Chunk newerThan(Long roomId, Long cursor, int size) {
        List<Message> fetched = messageRepository.findNewerByChatRoomIdWithSender(
                roomId, cursor, PageRequest.of(0, size + 1));
        boolean hasMore = fetched.size() > size;
        List<Message> page = new ArrayList<>(hasMore ? fetched.subList(0, size) : fetched);
        Collections.reverse(page);
        return new Chunk(page, hasMore);
    }

    private Long decodeRequiredId(String cursor) {
        if (cursor == null) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        Long id = messageCursorCodec.decode(cursor).id();
        if (id == null) {
            throw new BusinessException(CommonErrorCode.INVALID_CURSOR);
        }
        return id;
    }

    private void validateEcho(String cursor) {
        if (cursor != null) {
            decodeRequiredId(cursor);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String cursorOf(Message message) {
        return messageCursorCodec.encode(new MessageCursor(message.getId()));
    }

    private static Message last(List<Message> messages) {
        return messages.get(messages.size() - 1);
    }

    private List<MessageItem> toItems(List<Message> messages) {
        return messages.stream()
                .map(message -> MessageItem.from(message, senderProfileImageUrl(message)))
                .toList();
    }

    private record Chunk(List<Message> messages, boolean hasMore) {
    }

    private String senderProfileImageUrl(Message message) {
        User sender = message.getSender();
        return sender == null ? null : imageUrlResolver.toUrl(sender.getProfileImageUrl());
    }
}
