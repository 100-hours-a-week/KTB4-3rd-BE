package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.ACCEPTED;
import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.PENDING;
import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.REJECTED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.HandledJoinRequest;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
@Import({CarpoolRequestService.class, ChatSystemMessageService.class, CarpoolRequestHandleTest.Config.class})
class CarpoolRequestHandleTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private CarpoolRequestService carpoolRequestService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User requester;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
        requester = entityManager.persist(user("요청자"));
    }

    @Test
    @DisplayName("수락하면 요청이 수락되고, 요청자가 참여해 인원이 늘며 입장 메시지가 남는다")
    void accepts() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        ChatRoom chatRoom = entityManager.persist(ChatRoom.create(carpool));
        CompanionRequest request = persistRequest(carpool);

        HandledJoinRequest handled = carpoolRequestService.handle(host.getId(), carpool.getId(), request.getId(), ACCEPTED);

        flushAndClear();
        assertThat(statusOf(request)).isEqualTo(ACCEPTED);
        assertThat(participantOf(carpool, requester).getOutcomeStatus()).isEqualTo(OutcomeStatus.PENDING);
        assertThat(entityManager.find(Companion.class, carpool.getId()).getCurrentCount()).isEqualTo(2);
        assertThat(messagesOf(chatRoom)).singleElement().satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(MessageType.SYSTEM_JOIN);
            assertThat(message.getSender().getId()).isEqualTo(requester.getId());
        });
        assertThat(handled).isEqualTo(new HandledJoinRequest(request.getId(), ACCEPTED, chatRoom.getId(), 2, 4));
    }

    @Test
    @DisplayName("예전에 나간 사람을 수락하면 참여 행을 새로 만들지 않고 다시 살린다")
    void acceptsRejoin() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        entityManager.persist(ChatRoom.create(carpool));
        entityManager.persist(participant(carpool, requester, OutcomeStatus.INCOMPLETE, NOW.minusMinutes(10)));
        CompanionRequest request = persistRequest(carpool);

        carpoolRequestService.handle(host.getId(), carpool.getId(), request.getId(), ACCEPTED);

        flushAndClear();
        assertThat(participantsOf(carpool)).hasSize(2);
        CompanionParticipant rejoined = participantOf(carpool, requester);
        assertThat(rejoined.getOutcomeStatus()).isEqualTo(OutcomeStatus.PENDING);
        assertThat(rejoined.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("거절하면 요청만 거절되고 참여 · 인원 · 메시지는 그대로다")
    void rejects() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        ChatRoom chatRoom = entityManager.persist(ChatRoom.create(carpool));
        CompanionRequest request = persistRequest(carpool);

        HandledJoinRequest handled = carpoolRequestService.handle(host.getId(), carpool.getId(), request.getId(), REJECTED);

        flushAndClear();
        assertThat(statusOf(request)).isEqualTo(REJECTED);
        assertThat(participantsOf(carpool)).hasSize(1);
        assertThat(entityManager.find(Companion.class, carpool.getId()).getCurrentCount()).isEqualTo(1);
        assertThat(messagesOf(chatRoom)).isEmpty();
        assertThat(handled).isEqualTo(HandledJoinRequest.rejected(request.getId()));
    }

    @Test
    @DisplayName("방장이 아니면 HOST_ONLY이고 요청은 대기 그대로다")
    void rejectsNonHost() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        CompanionRequest request = persistRequest(carpool);

        assertErrorCode(() -> carpoolRequestService.handle(requester.getId(), carpool.getId(), request.getId(), ACCEPTED),
                CarpoolErrorCode.HOST_ONLY);
        assertThat(statusOf(request)).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("다른 카풀의 요청 · 없는 요청 · 없는 카풀 · 택시팟 id 는 CARPOOL_REQUEST_NOT_FOUND다")
    void notFound() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        Companion other = persistCarpool(RECRUITING, 1);
        CompanionRequest otherRequest = persistRequest(other);
        Companion pot = entityManager.persistAndFlush(taxiPot(host, RECRUITING, 1));

        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), carpool.getId(), otherRequest.getId(), ACCEPTED),
                CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND);
        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), carpool.getId(), otherRequest.getId() + 1000, ACCEPTED),
                CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND);
        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), pot.getId() + 1000, otherRequest.getId(), ACCEPTED),
                CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND);
        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), pot.getId(), otherRequest.getId(), ACCEPTED),
                CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND);
        assertThat(statusOf(otherRequest)).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("이미 처리된 요청은 수락 · 거절 모두 REQUEST_ALREADY_HANDLED다")
    void alreadyHandled() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        entityManager.persist(ChatRoom.create(carpool));
        CompanionRequest accepted = persistRequest(carpool);
        carpoolRequestService.handle(host.getId(), carpool.getId(), accepted.getId(), ACCEPTED);

        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), carpool.getId(), accepted.getId(), ACCEPTED),
                CarpoolErrorCode.REQUEST_ALREADY_HANDLED);
        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), carpool.getId(), accepted.getId(), REJECTED),
                CarpoolErrorCode.REQUEST_ALREADY_HANDLED);
        flushAndClear();
        assertThat(entityManager.find(Companion.class, carpool.getId()).getCurrentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("모집이 끝났거나 출발 시각이 지난 카풀은 수락할 수 없지만 거절은 된다")
    void closed() {
        Companion riding = persistCarpool(IN_PROGRESS, 2);
        CompanionRequest toRiding = persistRequest(riding);
        Companion departed = persistCarpool(RECRUITING, 1);
        ReflectionTestUtils.setField(departed, "departureAt", NOW.minusMinutes(1));
        CompanionRequest toDeparted = persistRequest(departed);

        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), riding.getId(), toRiding.getId(), ACCEPTED),
                CarpoolErrorCode.CARPOOL_CLOSED);
        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), departed.getId(), toDeparted.getId(), ACCEPTED),
                CarpoolErrorCode.CARPOOL_CLOSED);
        carpoolRequestService.handle(host.getId(), departed.getId(), toDeparted.getId(), REJECTED);

        assertThat(statusOf(toRiding)).isEqualTo(PENDING);
        assertThat(statusOf(toDeparted)).isEqualTo(REJECTED);
    }

    @Test
    @DisplayName("정원이 찬 카풀은 수락할 수 없다")
    void full() {
        Companion carpool = persistCarpool(RECRUITING, 4);
        CompanionRequest request = persistRequest(carpool);

        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), carpool.getId(), request.getId(), ACCEPTED),
                CarpoolErrorCode.CAPACITY_FULL);
        assertThat(statusOf(request)).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("탈퇴한 방장은 UNAUTHORIZED다")
    void withdrawnHost() {
        Companion carpool = persistCarpool(RECRUITING, 1);
        CompanionRequest request = persistRequest(carpool);
        host.withdraw(NOW);
        entityManager.flush();

        assertErrorCode(() -> carpoolRequestService.handle(host.getId(), carpool.getId(), request.getId(), ACCEPTED),
                CommonErrorCode.UNAUTHORIZED);
        assertThat(statusOf(request)).isEqualTo(PENDING);
    }

    private Companion persistCarpool(CompanionStatus status, int currentCount) {
        Companion carpool = entityManager.persist(carpool(host, status, currentCount));
        entityManager.persist(participant(carpool, host, OutcomeStatus.PENDING));
        entityManager.flush();
        return carpool;
    }

    private CompanionRequest persistRequest(Companion carpool) {
        CompanionRequest request = entityManager.persist(CompanionRequest.send(carpool, requester, "같이 가요"));
        entityManager.flush();
        return request;
    }

    private CompanionRequestStatus statusOf(CompanionRequest request) {
        flushAndClear();
        return entityManager.find(CompanionRequest.class, request.getId()).getStatus();
    }

    private List<CompanionParticipant> participantsOf(Companion carpool) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :id", CompanionParticipant.class)
                .setParameter("id", carpool.getId())
                .getResultList();
    }

    private CompanionParticipant participantOf(Companion carpool, User user) {
        return participantsOf(carpool).stream()
                .filter(participant -> participant.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    private List<Message> messagesOf(ChatRoom chatRoom) {
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.id = :id ORDER BY m.id", Message.class)
                .setParameter("id", chatRoom.getId())
                .getResultList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }
}
