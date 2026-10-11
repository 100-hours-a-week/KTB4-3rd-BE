package com.ktb.moyeota.domain.notification.push;

import java.util.List;
import java.util.stream.Collectors;

public record PushSendResult(
        List<TokenSendResult> results
) {

    public PushSendResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public boolean anySuccess() {
        return has(TokenSendOutcome.SUCCESS);
    }

    public boolean hasTransientFailure() {
        return has(TokenSendOutcome.TRANSIENT_FAILURE);
    }

    public List<String> unregisteredTokens() {
        return results.stream()
                .filter(result -> result.outcome() == TokenSendOutcome.UNREGISTERED)
                .map(TokenSendResult::token)
                .toList();
    }

    /** 실패한 토큰들의 오류 코드 요약. 예: "UNAVAILABLE,UNREGISTERED". 실패가 없으면 null */
    public String errorSummary() {
        String summary = results.stream()
                .map(TokenSendResult::errorCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return summary.isEmpty() ? null : summary;
    }

    private boolean has(TokenSendOutcome outcome) {
        return results.stream().anyMatch(result -> result.outcome() == outcome);
    }
}
