package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.service.CompanionParticipantChatMessageListener;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.event.ChatMessageBroadcaster;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.websocket.relay.WsRelayPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TaxiPotService.class, ChatSystemMessageService.class, CompanionParticipantChatMessageListener.class,
        ChatMessageBroadcaster.class, TestClockConfig.class})
class TaxiPotStartBroadcastTest {

    @Autowired
    private MutableClock clock;

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private WsRelayPublisher wsRelayPublisher;

    @BeforeEach
    void fixClock() {
        clock.set(DEPARTURE_AT.minusHours(1));
    }

    @Test
    @DisplayName("매칭이 커밋되면 채팅방 구독자에게 입장 메시지를 보낸다")
    void broadcastsEntryAfterCommit() {
        User me = userRepository.save(bankAccountHolder("첫사람"));

        CurrentTaxiPot pot = taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        verify(wsRelayPublisher).toRoom(eq(pot.chatRoomId()), eq("/sub/chat/" + pot.chatRoomId()), any(MessageItem.class));
    }
}
