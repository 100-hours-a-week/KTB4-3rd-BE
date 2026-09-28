package com.ktb.moyeota.global.security;

import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CHAT_SUBSCRIBE_PREFIX = "/sub/chat/";

    private final JwtDecoder jwtDecoder;
    private final CompanionParticipantRepository companionParticipantRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Long userId = authenticate(accessor.getFirstNativeHeader("Authorization"));
            accessor.setUser(userPrincipal(userId));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) && !isAuthorizedSubscription(accessor)) {
            return null;
        }

        return message;
    }

    private boolean isAuthorizedSubscription(StompHeaderAccessor accessor) {
        Long roomId = parseChatRoomId(accessor.getDestination());
        if (roomId == null) {
            return true;
        }

        Long userId = extractUserId(accessor.getUser());
        boolean authorized = userId != null
                && companionParticipantRepository.findActiveByChatRoomIdAndUserId(roomId, userId).isPresent();

        if (!authorized) {
            log.warn("[NON_PARTICIPANT_SUBSCRIBE] roomId={} userId={}", roomId, userId);
        }
        return authorized;
    }

    private Long parseChatRoomId(String destination) {
        if (destination == null || !destination.startsWith(CHAT_SUBSCRIBE_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(destination.substring(CHAT_SUBSCRIBE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long extractUserId(Principal user) {
        if (user == null) {
            return null;
        }
        try {
            return Long.valueOf(user.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return Long.valueOf(jwt.getSubject());
        } catch (JwtException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private Principal userPrincipal(Long userId) {
        String name = String.valueOf(userId);
        return () -> name;
    }
}
