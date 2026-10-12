package com.ktb.moyeota.domain.notification.push;

import java.util.List;


public interface PushSender {

    PushSendResult send(List<String> tokens, PushMessage message);
}
