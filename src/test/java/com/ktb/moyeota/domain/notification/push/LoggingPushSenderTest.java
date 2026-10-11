package com.ktb.moyeota.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingPushSenderTest {

    private final LoggingPushSender sender = new LoggingPushSender();

    @Test
    @DisplayName("실제로 보내지 않고 모든 토큰을 성공으로 돌려준다")
    void allSuccess() {
        PushMessage message = new PushMessage("모여타", "매칭이 완료됐어요", Map.of("notification_id", "1"));

        PushSendResult result = sender.send(List.of("t1", "t2"), message);

        assertThat(result.results()).extracting(TokenSendResult::token).containsExactly("t1", "t2");
        assertThat(result.results()).allMatch(r -> r.outcome() == TokenSendOutcome.SUCCESS);
    }
}
