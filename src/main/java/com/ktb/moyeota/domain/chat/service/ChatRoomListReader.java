package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;

public interface ChatRoomListReader {

    ChatRoomListResponse findMyChatRooms(Long userId, CompanionKind kind, String cursor);
}
