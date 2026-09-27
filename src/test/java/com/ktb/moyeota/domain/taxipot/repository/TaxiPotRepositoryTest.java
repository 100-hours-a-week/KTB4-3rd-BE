package com.ktb.moyeota.domain.taxipot.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class TaxiPotRepositoryTest {

    @Autowired
    private TaxiPotRepository taxiPotRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User me;

    @BeforeEach
    void setUp() {
        me = persist(user("길동이"));
    }

    @Test
    @DisplayName("참여 중인 택시팟을 찾는다")
    void findsWhilePending() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, PENDING));

        assertThat(taxiPotRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isPresent();
    }

    @Test
    @DisplayName("정산까지 마친 참여자도 찾을 수 있다")
    void findsAfterCompleted() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, COMPLETED));

        assertThat(taxiPotRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isPresent();
    }

    @Test
    @DisplayName("나간 참여자는 찾을 수 없다")
    void hiddenAfterLeaving() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, INCOMPLETE));

        assertThat(taxiPotRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isEmpty();
    }

    @Test
    @DisplayName("없는 id는 찾을 수 없다")
    void emptyForMissingId() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, PENDING));

        assertThat(taxiPotRepository.findTaxiPotForParticipant(pot.getId() + 1, me.getId())).isEmpty();
    }

    @Test
    @DisplayName("참여하지 않은 사람은 찾을 수 없다")
    void hiddenFromNonParticipant() {
        User other = persist(user("임꺽정"));
        Companion pot = persist(taxiPot(other, RECRUITING));
        persist(participant(pot, other, PENDING));

        assertThat(taxiPotRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isEmpty();
    }

    @Test
    @DisplayName("택시팟이 아닌 동행은 찾을 수 없다")
    void hiddenForOtherKinds() {
        Companion post = persist(companionPost(me));
        persist(participant(post, me, PENDING));

        assertThat(taxiPotRepository.findTaxiPotForParticipant(post.getId(), me.getId())).isEmpty();
    }

    @Nested
    @DisplayName("매칭 후보 찾기")
    class FindMatchable {

        @Test
        @DisplayName("출발지·도착지 좌표와 출발 시각이 모두 같고 자리가 있으면 찾는다")
        void findsSameConditions() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(taxiPotRepository.findMatchableTaxiPotForUpdate(
                    ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, DEPARTURE_AT))
                    .get().extracting(Companion::getId).isEqualTo(pot.getId());
        }

        @Test
        @DisplayName("출발 시각이 1분이라도 다르면 찾지 않는다")
        void differentDeparture() {
            persist(taxiPot(me, RECRUITING, 2));

            assertThat(taxiPotRepository.findMatchableTaxiPotForUpdate(
                    ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, DEPARTURE_AT.plusMinutes(1))).isEmpty();
        }

        @Test
        @DisplayName("정원이 찼으면 찾지 않는다")
        void full() {
            persist(taxiPot(me, RECRUITING, 4));

            assertThat(taxiPotRepository.findMatchableTaxiPotForUpdate(
                    ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, DEPARTURE_AT)).isEmpty();
        }

        @Test
        @DisplayName("모집 중이 아니면 찾지 않는다")
        void notRecruiting() {
            persist(taxiPot(me, IN_PROGRESS, 2));

            assertThat(taxiPotRepository.findMatchableTaxiPotForUpdate(
                    ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, DEPARTURE_AT)).isEmpty();
        }

        @Test
        @DisplayName("택시팟이 아닌 동행은 찾지 않는다")
        void otherKinds() {
            persist(companionPost(me));

            assertThat(taxiPotRepository.findMatchableTaxiPotForUpdate(
                    ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, DEPARTURE_AT)).isEmpty();
        }

        @Test
        @DisplayName("후보가 여럿이면 먼저 열린 팟을 찾는다")
        void oldestFirst() {
            Companion first = persist(taxiPot(me, RECRUITING, 2));
            persist(taxiPot(me, RECRUITING, 3));

            assertThat(taxiPotRepository.findMatchableTaxiPotForUpdate(
                    ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, DEPARTURE_AT))
                    .get().extracting(Companion::getId).isEqualTo(first.getId());
        }
    }

    @Nested
    @DisplayName("운행 시작 확인이 필요한 팟 찾기")
    class FindRideStartDue {

        private static final LocalDateTime NOW = DEPARTURE_AT.plusMinutes(1);

        @Test
        @DisplayName("모집 중이고 2명 이상이며 출발 시각이 지났으면 찾는다")
        void findsWhenDeparturePassed() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(taxiPotRepository.findRideStartDueIds(NOW)).containsExactly(pot.getId());
        }

        @Test
        @DisplayName("출발 시각이 딱 지금이어도 찾는다")
        void findsAtDeparture() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(taxiPotRepository.findRideStartDueIds(DEPARTURE_AT)).containsExactly(pot.getId());
        }

        @Test
        @DisplayName("출발 시각 전이면 찾지 않는다")
        void skipsBeforeDeparture() {
            persist(taxiPot(me, RECRUITING, 2));

            assertThat(taxiPotRepository.findRideStartDueIds(DEPARTURE_AT.minusMinutes(1))).isEmpty();
        }

        @Test
        @DisplayName("혼자면 찾지 않는다")
        void skipsWhenAlone() {
            persist(taxiPot(me, RECRUITING, 1));

            assertThat(taxiPotRepository.findRideStartDueIds(NOW)).isEmpty();
        }

        @Test
        @DisplayName("모집 중이 아니면 찾지 않는다")
        void skipsWhenNotRecruiting() {
            persist(taxiPot(me, IN_PROGRESS, 2));

            assertThat(taxiPotRepository.findRideStartDueIds(NOW)).isEmpty();
        }

        @Test
        @DisplayName("이미 운행 시작 확인 카드가 있으면 찾지 않는다")
        void skipsWhenAlreadyRequested() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));
            ChatRoom chatRoom = persist(ChatRoom.create(pot));
            persist(Message.rideStartRequestedSystemMessage(chatRoom, -1L, null));

            assertThat(taxiPotRepository.findRideStartDueIds(NOW)).isEmpty();
        }

        @Test
        @DisplayName("다른 시스템 메시지만 있으면 찾는다")
        void findsWithOtherMessages() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));
            ChatRoom chatRoom = persist(ChatRoom.create(pot));
            persist(Message.joinSystemMessage(chatRoom, me, -2L, null));

            assertThat(taxiPotRepository.findRideStartDueIds(NOW)).containsExactly(pot.getId());
        }
    }

    @Nested
    @DisplayName("운행 종료 확인이 필요한 팟 찾기")
    class FindRideEndDue {

        private static final LocalDateTime NOW = DEPARTURE_AT.plusHours(1);

        @Test
        @DisplayName("운행 중이고 도착 예정 시각이 지났으면 찾는다")
        void findsWhenEtaPassed() {
            Companion pot = riding(NOW.minusMinutes(1));

            assertThat(taxiPotRepository.findRideEndDueIds(NOW)).containsExactly(pot.getId());
        }

        @Test
        @DisplayName("도착 예정 시각이 딱 지금이어도 찾는다")
        void findsAtEta() {
            Companion pot = riding(NOW);

            assertThat(taxiPotRepository.findRideEndDueIds(NOW)).containsExactly(pot.getId());
        }

        @Test
        @DisplayName("도착 예정 시각 전이면 찾지 않는다")
        void skipsBeforeEta() {
            riding(NOW.plusMinutes(1));

            assertThat(taxiPotRepository.findRideEndDueIds(NOW)).isEmpty();
        }

        @Test
        @DisplayName("운행 중이 아니면 찾지 않는다")
        void skipsWhenNotRiding() {
            Companion pot = riding(NOW.minusMinutes(1));
            ReflectionTestUtils.setField(pot, "status", CompanionStatus.COMPLETED);
            persist(pot);

            assertThat(taxiPotRepository.findRideEndDueIds(NOW)).isEmpty();
        }

        @Test
        @DisplayName("이미 운행 종료 확인 카드가 있으면 찾지 않는다")
        void skipsWhenAlreadyRequested() {
            Companion pot = riding(NOW.minusMinutes(1));
            ChatRoom chatRoom = persist(ChatRoom.create(pot));
            persist(Message.rideEndRequestedSystemMessage(chatRoom, -1L, null));

            assertThat(taxiPotRepository.findRideEndDueIds(NOW)).isEmpty();
        }

        @Test
        @DisplayName("다른 시스템 메시지만 있으면 찾는다")
        void findsWithOtherMessages() {
            Companion pot = riding(NOW.minusMinutes(1));
            ChatRoom chatRoom = persist(ChatRoom.create(pot));
            persist(Message.joinSystemMessage(chatRoom, me, -2L, null));

            assertThat(taxiPotRepository.findRideEndDueIds(NOW)).containsExactly(pot.getId());
        }

        private Companion riding(LocalDateTime etaAt) {
            Companion pot = taxiPot(me, IN_PROGRESS, 2);
            ReflectionTestUtils.setField(pot, "etaAt", etaAt);
            return persist(pot);
        }
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
