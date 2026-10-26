package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.taxipot.dto.TaxiPotMatchedMessage;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;
import com.ktb.moyeota.global.websocket.UserChannel;
import com.ktb.moyeota.global.websocket.UserEventSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TaxiPotMatchedEventListener {

    private final UserEventSender userEventSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatched(TaxiPotMatchedEvent event) {
        TaxiPotMatchedMessage message = TaxiPotMatchedMessage.from(event);
        event.matchedUserIds().forEach(userId -> userEventSender.send(userId, UserChannel.TAXI_POT, message));
    }
}
