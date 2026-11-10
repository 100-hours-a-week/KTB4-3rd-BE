package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "direct",
        matchIfMissing = true)
public class TaxiPotMatchChatMessageListener {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatSystemMessageService chatSystemMessageService;

    @EventListener
    public void onMatched(TaxiPotMatchedEvent event) {
        ChatRoom chatRoom = chatRoomRepository.findById(event.chatRoomId())
                .orElseThrow(() -> new IllegalStateException("채팅방이 없다: " + event.chatRoomId()));
        Map<Long, User> users = userRepository.findAllById(event.matchedUserIds()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        event.matchedUserIds().forEach(userId -> chatSystemMessageService.enter(chatRoom, users.get(userId)));
    }
}
