package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomCloseService {

    private final ChatRoomRepository chatRoomRepository;
    private final Clock clock;

    @Transactional
    public int closeAfterRide() {
        LocalDateTime now = LocalDateTime.now(clock);
        return chatRoomRepository.closeCompletedBefore(now.minus(ChatRoom.OPEN_PERIOD_AFTER_RIDE), now);
    }
}
