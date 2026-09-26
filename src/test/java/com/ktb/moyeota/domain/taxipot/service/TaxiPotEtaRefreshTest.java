package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.config.AsyncConfig;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TaxiPotService.class, ChatSystemMessageService.class, EtaRefresher.class, AsyncConfig.class,
        TaxiPotEtaRefreshTest.FixedClock.class})
class TaxiPotEtaRefreshTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaxiPotRepository taxiPotRepository;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @MockitoBean
    private KakaoNaviClient kakaoNaviClient;

    @Test
    @DisplayName("운행 시작이 커밋되면 길찾기 소요 시간으로 도착 예정 시각을 다시 잡는다")
    void refreshesEtaAfterCommit() {
        given(kakaoNaviClient.estimateDuration(any(), any(), any(), any()))
                .willReturn(Optional.of(Duration.ofMinutes(20)));
        User host = userRepository.save(user("방장"));
        Companion pot = taxiPotRepository.save(taxiPot(host, RECRUITING, 2));
        chatRoomRepository.save(ChatRoom.create(pot));
        taxiPotParticipantRepository.save(participant(pot, host, PENDING));

        taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(taxiPotRepository.findById(pot.getId()).orElseThrow().getEtaAt())
                        .isEqualTo(DEPARTURE_AT.plusMinutes(20)));
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.atZone(kst).toInstant(), kst);
        }
    }
}
