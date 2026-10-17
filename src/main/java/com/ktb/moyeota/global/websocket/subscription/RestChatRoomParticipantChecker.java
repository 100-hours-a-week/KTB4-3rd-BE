package com.ktb.moyeota.global.websocket.subscription;

import com.ktb.moyeota.domain.chat.dto.ParticipantCheckResponse;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@ConditionalOnServerRole(ServerRole.WEBSOCKET)
@RequiredArgsConstructor
public class RestChatRoomParticipantChecker implements ChatRoomParticipantChecker {

    private static final String PATH = "/internal/chat-rooms/{room_id}/participants/{user_id}";

    private final RestClient internalRestClient;

    @Override
    public boolean isParticipant(Long roomId, Long userId) {
        try {
            Envelope response = internalRestClient.get()
                    .uri(PATH, roomId, userId)
                    .retrieve()
                    .body(Envelope.class);
            return response != null && response.data() != null && response.data().isParticipant();
        } catch (RestClientResponseException e) {
            log.error("[SUBSCRIBE_CHECK_FAILED] 참여 여부 확인 응답이 실패했습니다. roomId={} userId={} status={}",
                    roomId, userId, e.getStatusCode().value());
            return false;
        } catch (RestClientException e) {
            log.warn("[SUBSCRIBE_CHECK_FAILED] rest 서버에 참여 여부를 묻지 못했습니다. roomId={} userId={}",
                    roomId, userId, e);
            return false;
        }
    }

    record Envelope(ParticipantCheckResponse data) {
    }
}
