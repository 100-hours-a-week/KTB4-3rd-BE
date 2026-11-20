package com.ktb.moyeota.domain.notification;

import com.ktb.moyeota.domain.notification.push.PushMessage;
import com.ktb.moyeota.domain.notification.push.PushSendResult;
import com.ktb.moyeota.domain.notification.push.PushSender;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import com.ktb.moyeota.domain.notification.push.TokenSendResult;
import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

class ScriptedPushSender implements PushSender {

    private final Map<String, Deque<TokenSendOutcome>> scripts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final Set<Long> failOnce = ConcurrentHashMap.newKeySet();
    private volatile Duration delay = Duration.ZERO;

    void script(String token, TokenSendOutcome... outcomes) {
        scripts.put(token, new ConcurrentLinkedDeque<>(List.of(outcomes)));
    }

    void delay(Duration delay) {
        this.delay = delay;
    }

    int callsOf(String token) {
        AtomicInteger count = calls.get(token);
        return count == null ? 0 : count.get();
    }

    void failNotificationOnce(Long notificationId) {
        failOnce.add(notificationId);
    }

    @Override
    public PushSendResult send(List<String> tokens, PushMessage message) {
        pause();
        if (failOnce.remove(Long.parseLong(message.data().get("notification_id")))) {
            return new PushSendResult(tokens.stream()
                    .map(token -> TokenSendResult.failure(
                            token, TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.TRANSIENT_FAILURE.name()))
                    .toList());
        }
        return new PushSendResult(tokens.stream().map(this::sendOne).toList());
    }

    private TokenSendResult sendOne(String token) {
        calls.computeIfAbsent(token, ignored -> new AtomicInteger()).incrementAndGet();
        Deque<TokenSendOutcome> script = scripts.get(token);
        TokenSendOutcome outcome = script == null ? null : script.poll();
        if (outcome == null || outcome == TokenSendOutcome.SUCCESS) {
            return TokenSendResult.success(token);
        }
        return TokenSendResult.failure(token, outcome, outcome.name());
    }

    private void pause() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
