package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatRoomCursor;
import com.ktb.moyeota.domain.chat.dto.ChatRoomItem;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.repository.ChatRoomListProjection;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mysql", matchIfMissing = true)
public class MysqlChatRoomListReader implements ChatRoomListReader {

    private static final int PAGE_SIZE = 10;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomCursorCodec chatRoomCursorCodec;
    private final ImageUrlResolver imageUrlResolver;

    @Override
    @Transactional(readOnly = true)
    public ChatRoomListResponse findMyChatRooms(Long userId, CompanionKind kind, String cursor) {
        ChatRoomCursor decoded = chatRoomCursorCodec.decode(cursor);
        String kindParam = (kind == null) ? null : kind.name();

        List<ChatRoomListProjection> fetched = chatRoomRepository.findMyChatRooms(
                userId, kindParam, decoded.lastMessageAt(), decoded.roomId(), PAGE_SIZE + 1);

        boolean hasNext = fetched.size() > PAGE_SIZE;
        List<ChatRoomListProjection> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;

        String nextCursor = null;
        if (hasNext) {
            ChatRoomListProjection last = page.get(page.size() - 1);
            nextCursor = chatRoomCursorCodec.encode(new ChatRoomCursor(last.getLastMessageAt(), last.getId()));
        }

        List<ChatRoomItem> items = page.stream()
                .map(row -> ChatRoomItem.of(row, imageUrlResolver.toUrl(row.getHostProfileImageUrl()), hasUnread(row)))
                .toList();

        return new ChatRoomListResponse(items, nextCursor);
    }

    private boolean hasUnread(ChatRoomListProjection row) {
        Long lastMessageId = row.getLastMessageId();
        Long lastReadMessageId = row.getLastReadMessageId();
        return lastMessageId != null && (lastReadMessageId == null || lastMessageId > lastReadMessageId);
    }
}
