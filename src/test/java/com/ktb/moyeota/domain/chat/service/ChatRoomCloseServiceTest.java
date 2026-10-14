package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({ChatRoomCloseService.class, ChatRoomCloseServiceTest.FixedClock.class})
class ChatRoomCloseServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Autowired
    private ChatRoomCloseService chatRoomCloseService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
    }

    @Test
    @DisplayName("운행을 마친 지 48시간이 지난 채팅방을 닫는다")
    void closesRoomAfterOpenPeriod() {
        ChatRoom chatRoom = roomOf(COMPLETED, NOW.minusHours(48));

        chatRoomCloseService.closeAfterRide();

        assertThat(reload(chatRoom).getClosedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("운행을 마친 지 48시간이 안 된 채팅방은 열어 둔다")
    void keepsRoomWithinOpenPeriod() {
        ChatRoom chatRoom = roomOf(COMPLETED, NOW.minusHours(48).plusMinutes(1));

        chatRoomCloseService.closeAfterRide();

        assertThat(reload(chatRoom).getClosedAt()).isNull();
    }

    @Test
    @DisplayName("완주한 사람이 없는 채팅방은 열어 둔다")
    void keepsRoomWithoutCompletion() {
        ChatRoom chatRoom = roomOf(INCOMPLETE, NOW.minusDays(3));

        chatRoomCloseService.closeAfterRide();

        assertThat(reload(chatRoom).getClosedAt()).isNull();
    }

    @Test
    @DisplayName("이미 닫힌 채팅방의 닫힌 시각은 바꾸지 않는다")
    void keepsEarlierClosedAt() {
        ChatRoom chatRoom = roomOf(COMPLETED, NOW.minusDays(3));
        LocalDateTime closedAt = NOW.minusDays(2);
        ReflectionTestUtils.setField(chatRoom, "closedAt", closedAt);
        entityManager.flush();

        chatRoomCloseService.closeAfterRide();

        assertThat(reload(chatRoom).getClosedAt()).isEqualTo(closedAt);
    }

    private ChatRoom roomOf(OutcomeStatus outcome, LocalDateTime leftAt) {
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(participant(pot, host, outcome, leftAt));
        return entityManager.persistAndFlush(ChatRoom.create(pot));
    }

    private ChatRoom reload(ChatRoom chatRoom) {
        entityManager.clear();
        return entityManager.find(ChatRoom.class, chatRoom.getId());
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
