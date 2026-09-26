package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatRoomCursor;
import com.ktb.moyeota.domain.chat.dto.ChatRoomDetailResponse;
import com.ktb.moyeota.domain.chat.dto.ChatRoomItem;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatRoomListProjection;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final int PAGE_SIZE = 10;

    private final ChatRoomRepository chatRoomRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomCursorCodec chatRoomCursorCodec;

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
                .map(row -> ChatRoomItem.of(row, hasUnread(row)))
                .toList();

        return new ChatRoomListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse findDetail(Long userId, Long roomId) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        ChatRoom chatRoom = chatRoomRepository.findByIdWithCompanion(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        Long lastReadMessageId = (participant.getMessage() != null) ? participant.getMessage().getId() : null;

        return ChatRoomDetailResponse.of(chatRoom, lastReadMessageId);
    }

    @Transactional
    public ReadMarkerResponse markRead(Long userId, Long roomId, ReadMarkerRequest request) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        Long currentLastReadMessageId = (participant.getMessage() != null) ? participant.getMessage().getId() : null;
        Long requestedLastReadMessageId = request.lastReadMessageId();

        Long finalLastReadMessageId = currentLastReadMessageId;
        if (requestedLastReadMessageId != null) {
            Message messageRef = messageRepository.getReferenceById(requestedLastReadMessageId);
            int updatedRows = companionParticipantRepository.updateLastReadMessageIfNewer(
                    participant.getId(), messageRef, requestedLastReadMessageId);
            if (updatedRows > 0) {
                finalLastReadMessageId = requestedLastReadMessageId;
            }
        }

        return new ReadMarkerResponse(finalLastReadMessageId);
    }

    @Transactional
    public void createChatRoomForCompanion(Companion companion, User host) {
        chatRoomRepository.save(ChatRoom.create(companion));
        companionParticipantRepository.save(CompanionParticipant.join(companion, host));
    }

    private boolean hasUnread(ChatRoomListProjection row) {
        Long lastMessageId = row.getLastMessageId();
        Long lastReadMessageId = row.getLastReadMessageId();
        return lastMessageId != null && (lastReadMessageId == null || lastMessageId > lastReadMessageId);
    }
}
