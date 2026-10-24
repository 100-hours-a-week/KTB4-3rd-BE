package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchingRequest;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.AlreadyApplied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Applied;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TaxiPotMatchReflector.class, ChatSystemMessageService.class,
        TaxiPotMatchReflectorConcurrencyTest.FixedClock.class})
class TaxiPotMatchReflectorConcurrencyTest {

    @Autowired
    private TaxiPotMatchReflector reflector;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;

    @Autowired
    private TaxiPotRepository taxiPotRepository;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private MessageRepository messageRepository;

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        taxiPotParticipantRepository.deleteAll();
        taxiPotMatchRequestRepository.deleteAll();
        taxiPotRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 새 팟 결과를 두 반영기가 동시에 반영해도 팟은 하나이고 한쪽은 이미 반영됨이다")
    void concurrentOpenCreatesOnePot() {
        List<TaxiPotMatchRequest> requests = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> {
                    User user = userRepository.save(bankAccountHolder("요청자" + i));
                    return taxiPotMatchRequestRepository.save(
                            matchingRequest(user, "출발지" + i, "도착지" + i, DEPARTURE_AT));
                })
                .toList();
        Long anchor = requests.getFirst().getId();
        MatchResult result = MatchResult.openPot(
                requests.stream().map(request -> matchRequest(request.getId())).toList(), anchor, anchor, DEPARTURE_AT);

        CountDownLatch ready = new CountDownLatch(1);
        List<CompletableFuture<MatchApplyOutcome>> futures = IntStream.range(0, 2)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    awaitQuietly(ready);
                    return reflector.apply(result);
                }))
                .toList();
        ready.countDown();
        List<MatchApplyOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

        Long potId = taxiPotRepository.findAll().getFirst().getId();
        assertThat(outcomes).containsExactlyInAnyOrder(new Applied(potId), new AlreadyApplied(potId));
        assertThat(taxiPotRepository.count()).isEqualTo(1);
        assertThat(taxiPotParticipantRepository.count()).isEqualTo(3);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.minusHours(1).atZone(kst).toInstant(), kst);
        }
    }
}
