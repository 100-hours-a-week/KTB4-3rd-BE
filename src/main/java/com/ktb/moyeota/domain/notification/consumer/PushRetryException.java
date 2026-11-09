package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;

public class PushRetryException extends RuntimeException {

    public PushRetryException(long jobId, PushDeliveryOutcome outcome) {
        super("푸시 발송이 일시 오류로 실패했습니다. jobId=" + jobId + " outcome=" + outcome);
    }
}
