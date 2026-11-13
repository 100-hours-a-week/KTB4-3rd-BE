package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Sender;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Settlement;
import com.ktb.moyeota.domain.chat.dto.MessageItem.SystemActor;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.settlement.service.SettlementSummaryReader;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MongoDB {@code chat_messages} 에서 방별 번호(seq) 기준 v2 커서로 이전 · 새 메시지를 읽는다.
 * 보낸 사람 · 입장 · 퇴장한 사람은 한 페이지 것을 모아 {@code users} 를 한 번에 읽고, 정산 카드 값은 응답할 때 정산 행에서 붙인다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mongo")
public class MongoChatMessageService implements ChatMessageReader {

    private static final int PAGE_SIZE = 20;
    private static final int ANCHOR_SIDE_SIZE = 10;
    private static final String DIRECTION_BEFORE = "before";
    private static final String DIRECTION_AFTER = "after";
    private static final Set<MessageType> ACTOR_TYPES = Set.of(MessageType.SYSTEM_JOIN, MessageType.SYSTEM_LEAVE);

    private final ChatMessageRepository chatMessageRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final UserRepository userRepository;
    private final MessageSeqCursorCodec cursorCodec;
    private final ImageUrlResolver imageUrlResolver;
    private final SettlementSummaryReader settlementSummaryReader;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MessageListResponse<ChatMessageItem> findMessages(
            Long userId, Long roomId, String direction, String before, String after) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));
        Companion companion = participant.getCompanion();

        String beforeCursor = blankToNull(before);
        String afterCursor = blankToNull(after);
        String dir = blankToNull(direction);

        if (dir == null) {
            if (beforeCursor != null || afterCursor != null) {
                throw new BusinessException(ChatErrorCode.INVALID_DIRECTION);
            }
            return findFirstEntry(roomId, companion, lastReadSeqOf(participant));
        }
        if (DIRECTION_BEFORE.equals(dir)) {
            long beforeSeq = cursorCodec.decode(beforeCursor);
            validateEcho(afterCursor);
            Chunk older = olderThan(roomId, beforeSeq, PAGE_SIZE);
            return new MessageListResponse<>(toItems(older.messages(), companion),
                    older.hasMore() ? cursorOf(last(older.messages())) : null, afterCursor);
        }
        if (DIRECTION_AFTER.equals(dir)) {
            long afterSeq = cursorCodec.decode(afterCursor);
            validateEcho(beforeCursor);
            Chunk newer = newerThan(roomId, afterSeq, PAGE_SIZE);
            return new MessageListResponse<>(toItems(newer.messages(), companion),
                    beforeCursor, newer.hasMore() ? cursorOf(newer.messages().get(0)) : null);
        }
        throw new BusinessException(ChatErrorCode.INVALID_DIRECTION);
    }

    /**
     * 읽음 위치(seq)는 companion_participants.last_read_seq 로 바뀐 뒤 채운다. 그 전까지는 가장 최근 페이지부터 보여 준다.
     */
    private Long lastReadSeqOf(CompanionParticipant participant) {
        return null;
    }

    private MessageListResponse<ChatMessageItem> findFirstEntry(Long roomId, Companion companion, Long lastReadSeq) {
        if (lastReadSeq != null) {
            Chunk unread = newerThan(roomId, lastReadSeq, ANCHOR_SIDE_SIZE);
            if (!unread.messages().isEmpty()) {
                Chunk read = olderThan(roomId, lastReadSeq + 1, ANCHOR_SIDE_SIZE);
                List<ChatMessage> merged = new ArrayList<>(unread.messages());
                merged.addAll(read.messages());
                return new MessageListResponse<>(toItems(merged, companion),
                        read.hasMore() && !read.messages().isEmpty() ? cursorOf(last(read.messages())) : null,
                        unread.hasMore() ? cursorOf(unread.messages().get(0)) : null);
            }
        }
        Chunk latest = olderThan(roomId, null, PAGE_SIZE);
        return new MessageListResponse<>(toItems(latest.messages(), companion),
                latest.hasMore() ? cursorOf(last(latest.messages())) : null, null);
    }

    private Chunk olderThan(Long roomId, Long beforeSeq, int size) {
        List<ChatMessage> fetched = chatMessageRepository.findOlder(roomId, beforeSeq, size + 1);
        boolean hasMore = fetched.size() > size;
        return new Chunk(hasMore ? fetched.subList(0, size) : fetched, hasMore);
    }

    private Chunk newerThan(Long roomId, long afterSeq, int size) {
        List<ChatMessage> fetched = chatMessageRepository.findNewer(roomId, afterSeq, size + 1);
        boolean hasMore = fetched.size() > size;
        List<ChatMessage> page = new ArrayList<>(hasMore ? fetched.subList(0, size) : fetched);
        Collections.reverse(page);
        return new Chunk(page, hasMore);
    }

    private List<ChatMessageItem> toItems(List<ChatMessage> messages, Companion companion) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = usersOf(messages);
        Settlement settlement = settlementOf(messages, companion);
        return messages.stream()
                .map(message -> toItem(message, users.get(message.getSenderId()), companion, settlement))
                .toList();
    }

    private ChatMessageItem toItem(ChatMessage message, User user, Companion companion, Settlement settlement) {
        if (message.getType() == MessageType.TEXT) {
            return ChatMessageItem.text(message, senderOf(message.getSenderId(), user, companion), clock.getZone());
        }
        SystemActor actor = ACTOR_TYPES.contains(message.getType()) && message.getSenderId() != null
                ? new SystemActor(message.getSenderId(), user == null ? null : user.getName())
                : null;
        return ChatMessageItem.system(message, actor, settlement, clock.getZone());
    }

    private Sender senderOf(Long senderId, User user, Companion companion) {
        if (user == null) {
            return senderId == null ? null : new Sender(senderId, null, null, null);
        }
        return MessageItem.senderOf(user, companion.getKind(), imageUrlResolver.toUrl(user.getProfileImageUrl()));
    }

    private Map<Long, User> usersOf(List<ChatMessage> messages) {
        Set<Long> ids = messages.stream()
                .filter(message -> message.getType() == MessageType.TEXT || ACTOR_TYPES.contains(message.getType()))
                .map(ChatMessage::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Settlement settlementOf(List<ChatMessage> messages, Companion companion) {
        boolean hasTransferCard = messages.stream()
                .anyMatch(message -> message.getType() == MessageType.SYSTEM_TRANSFER_REQUESTED);
        if (!hasTransferCard) {
            return null;
        }
        return settlementSummaryReader.findByCompanionId(companion.getId()).orElse(null);
    }

    private void validateEcho(String cursor) {
        if (cursor != null) {
            cursorCodec.decode(cursor);
        }
    }

    private String cursorOf(ChatMessage message) {
        return cursorCodec.encode(message.getSeq());
    }

    private static ChatMessage last(List<ChatMessage> messages) {
        return messages.get(messages.size() - 1);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Chunk(List<ChatMessage> messages, boolean hasMore) {
    }
}
