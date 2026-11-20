package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatRoomCursor;
import com.ktb.moyeota.domain.chat.dto.ChatRoomItem;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.LastMessage;
import com.ktb.moyeota.domain.chat.repository.MyChatRoomProjection;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mongo")
public class MongoChatRoomListReader implements ChatRoomListReader {

    private static final int PAGE_SIZE = 10;
    private static final Comparator<ChatRoomCursor> LATEST_FIRST = Comparator
            .comparing(ChatRoomCursor::lastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ChatRoomCursor::roomId, Comparator.reverseOrder());

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomCursorCodec chatRoomCursorCodec;
    private final ImageUrlResolver imageUrlResolver;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ChatRoomListResponse findMyChatRooms(Long userId, CompanionKind kind, String cursor) {
        ChatRoomCursor decoded = chatRoomCursorCodec.decode(cursor);
        List<MyChatRoomProjection> rooms = chatRoomRepository.findMyOpenChatRooms(
                userId, kind == null ? null : kind.name());
        Map<Long, LastMessage> lastMessages = chatMessageRepository.findLastMessages(
                rooms.stream().map(MyChatRoomProjection::getId).toList());

        List<Row> fetched = rooms.stream()
                .map(room -> new Row(room, lastMessages.get(room.getId())))
                .sorted(Comparator.comparing(row -> row.position(clock), LATEST_FIRST))
                .filter(row -> decoded.roomId() == null || LATEST_FIRST.compare(row.position(clock), decoded) > 0)
                .limit(PAGE_SIZE + 1)
                .toList();

        boolean hasNext = fetched.size() > PAGE_SIZE;
        List<Row> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;
        String nextCursor = hasNext ? chatRoomCursorCodec.encode(page.get(page.size() - 1).position(clock)) : null;

        List<ChatRoomItem> items = page.stream()
                .map(row -> ChatRoomItem.of(row.room(),
                        imageUrlResolver.toUrl(row.room().getHostProfileImageUrl()), row.hasUnread()))
                .toList();
        return new ChatRoomListResponse(items, nextCursor);
    }

    private record Row(MyChatRoomProjection room, LastMessage lastMessage) {

        ChatRoomCursor position(Clock clock) {
            LocalDateTime lastMessageAt = lastMessage == null
                    ? null
                    : LocalDateTime.ofInstant(lastMessage.lastMessageAt(), clock.getZone());
            return new ChatRoomCursor(lastMessageAt, room.getId());
        }

        boolean hasUnread() {
            Long lastReadSeq = room.getLastReadSeq();
            return lastMessage != null && (lastReadSeq == null || lastMessage.lastSeq() > lastReadSeq);
        }
    }
}
