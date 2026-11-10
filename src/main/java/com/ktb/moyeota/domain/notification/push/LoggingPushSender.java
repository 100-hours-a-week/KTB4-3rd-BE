package com.ktb.moyeota.domain.notification.push;

import java.util.List;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushSendResult send(List<String> tokens, PushMessage message) {
        log.info("[PUSH_SKIPPED_LOCAL] tokens={}, title={}, body={}, data={}",
                tokens.size(), message.title(), message.body(), message.data());
        return new PushSendResult(tokens.stream().map(TokenSendResult::success).toList());
    }
}
