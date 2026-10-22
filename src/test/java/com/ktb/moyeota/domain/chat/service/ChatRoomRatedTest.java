package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.rating.entity.CompanionRating;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
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
@Import({ChatRoomService.class, ChatRoomCursorCodec.class, ChatRoomRatedTest.Config.class})
class ChatRoomRatedTest {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(user("동승자"));
    }

    @Test
    @DisplayName("완주한 카풀은 평가 전이면 rated 가 false, 평가하면 true 다")
    void completedCarpool() {
        Companion carpool = persistCompanion(carpool(host, CompanionStatus.COMPLETED, 2), COMPLETED);
        ChatRoom chatRoom = persistChatRoom(carpool);
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isFalse();

        entityManager.persist(CompanionRating.of(entityManager.find(Companion.class, carpool.getId()),
                entityManager.find(User.class, rider.getId()), entityManager.find(User.class, host.getId()), 5));
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isTrue();
        assertThat(ratedOf(host, chatRoom)).isFalse();
    }

    @Test
    @DisplayName("운행이 끝나지 않은 카풀은 rated 가 null 이다")
    void carpoolInProgress() {
        Companion carpool = persistCompanion(carpool(host, CompanionStatus.IN_PROGRESS, 2), PENDING);
        ChatRoom chatRoom = persistChatRoom(carpool);
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isNull();
    }

    @Test
    @DisplayName("완주한 택시팟은 정산이 끝나야 rated 를 채운다")
    void taxiPotWaitsForSettlement() {
        Companion pot = persistCompanion(taxiPot(host, CompanionStatus.COMPLETED, 2), COMPLETED);
        ChatRoom chatRoom = persistChatRoom(pot);
        Settlement settlement = entityManager.persist(Settlement.open(pot));
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isNull();

        settlement = entityManager.find(Settlement.class, settlement.getId());
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REQUESTED);
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isNull();

        settlement = entityManager.find(Settlement.class, settlement.getId());
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.COMPLETED);
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isFalse();
    }

    @Test
    @DisplayName("완주하지 못한 참여자에게는 rated 가 null 이다")
    void viewerNotCompleted() {
        Companion carpool = entityManager.persist(carpool(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(participant(carpool, host, COMPLETED));
        entityManager.persist(participant(carpool, rider, PENDING));
        ChatRoom chatRoom = persistChatRoom(carpool);
        flushAndClear();

        assertThat(ratedOf(rider, chatRoom)).isNull();
        assertThat(ratedOf(host, chatRoom)).isNull();
    }

    @Test
    @DisplayName("함께 완주한 사람이 없으면 평가할 대상이 없어 rated 가 null 이다")
    void noOneToRate() {
        Companion carpool = entityManager.persist(carpool(host, CompanionStatus.COMPLETED, 1));
        entityManager.persist(participant(carpool, host, COMPLETED));
        ChatRoom chatRoom = persistChatRoom(carpool);
        flushAndClear();

        assertThat(ratedOf(host, chatRoom)).isNull();
    }

    private Boolean ratedOf(User viewer, ChatRoom chatRoom) {
        return chatRoomService.findDetail(viewer.getId(), chatRoom.getId()).rated();
    }

    private Companion persistCompanion(Companion companion, OutcomeStatus outcome) {
        entityManager.persist(companion);
        entityManager.persist(participant(companion, host, outcome));
        entityManager.persist(participant(companion, rider, outcome));
        return companion;
    }

    private ChatRoom persistChatRoom(Companion companion) {
        return entityManager.persist(ChatRoom.create(companion));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class Config {

        @Bean
        ImageUrlResolver imageUrlResolver() {
            return new ImageUrlResolver(new S3Properties(
                    "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test"));
        }
    }
}
