package com.ktb.moyeota.global.external.firebase;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.ktb.moyeota.domain.notification.push.PushMessage;
import com.ktb.moyeota.domain.notification.push.PushSendResult;
import com.ktb.moyeota.domain.notification.push.PushSender;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import com.ktb.moyeota.domain.notification.push.TokenSendResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    static final int MAX_TOKENS_PER_REQUEST = 500;

    private static final String UNKNOWN_ERROR = "UNKNOWN";

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushSendResult send(List<String> tokens, PushMessage message) {
        List<TokenSendResult> results = new ArrayList<>();
        for (int from = 0; from < tokens.size(); from += MAX_TOKENS_PER_REQUEST) {
            List<String> chunk = tokens.subList(from, Math.min(from + MAX_TOKENS_PER_REQUEST, tokens.size()));
            results.addAll(sendChunk(chunk, message));
        }
        return new PushSendResult(results);
    }

    private List<TokenSendResult> sendChunk(List<String> tokens, PushMessage message) {
        try {
            BatchResponse batch = firebaseMessaging.sendEachForMulticast(toMulticast(tokens, message));
            return toResults(tokens, batch.getResponses());
        } catch (FirebaseMessagingException e) {
            String errorCode = errorCodeOf(e);
            log.warn("[FCM_REQUEST_FAILED] tokens={}, errorCode={}, message={}", tokens.size(), errorCode, e.getMessage());
            return tokens.stream()
                    .map(token -> TokenSendResult.failure(token, TokenSendOutcome.TRANSIENT_FAILURE, errorCode))
                    .toList();
        }
    }

    private static MulticastMessage toMulticast(List<String> tokens, PushMessage message) {
        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
    }

    static List<TokenSendResult> toResults(List<String> tokens, List<SendResponse> responses) {
        List<TokenSendResult> results = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                results.add(TokenSendResult.success(token));
                continue;
            }
            FirebaseMessagingException e = response.getException();
            MessagingErrorCode code = e == null ? null : e.getMessagingErrorCode();
            TokenSendOutcome outcome = toOutcome(code);
            if (outcome == TokenSendOutcome.PERMANENT_FAILURE) {
                log.warn("[FCM_TOKEN_FAILED] errorCode={}, message={}", code, e == null ? null : e.getMessage());
            }
            results.add(TokenSendResult.failure(token, outcome, e == null ? UNKNOWN_ERROR : errorCodeOf(e)));
        }
        return results;
    }

    static TokenSendOutcome toOutcome(MessagingErrorCode code) {
        if (code == null) {
            return TokenSendOutcome.TRANSIENT_FAILURE;
        }
        return switch (code) {
            case UNREGISTERED -> TokenSendOutcome.UNREGISTERED;
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> TokenSendOutcome.TRANSIENT_FAILURE;
            case INVALID_ARGUMENT, SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR -> TokenSendOutcome.PERMANENT_FAILURE;
            default -> TokenSendOutcome.PERMANENT_FAILURE;
        };
    }

    private static String errorCodeOf(FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() != null) {
            return e.getMessagingErrorCode().name();
        }
        return e.getErrorCode() == null ? UNKNOWN_ERROR : e.getErrorCode().name();
    }
}
