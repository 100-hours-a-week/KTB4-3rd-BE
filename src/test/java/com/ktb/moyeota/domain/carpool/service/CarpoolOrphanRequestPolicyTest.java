package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.ACCEPTED;
import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.PENDING;
import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.REJECTED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.entity.RejectReason;
import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.scheduler.CarpoolRequestExpireScheduler;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 카풀 고아 요청 정책 (BE #363): 호스트 거절 · 정원 마감 즉시 거절 · 재신청 제한 · 출발 경과 sweep.
 * 알림은 실제 NotificationCommandService 로 저장해 notifications 행을 직접 확인한다.
 */
@DataJpaTest
@Import({CarpoolRequestService.class, ChatSystemMessageService.class, NotificationCommandService.class,
        CarpoolRequestExpireScheduler.class, TestClockConfig.class})
class CarpoolOrphanRequestPolicyTest {

    private static final LocalDateTime BEFORE_DEPARTURE = DEPARTURE_AT.minusHours(1);
    private static final LocalDateTime AFTER_DEPARTURE = DEPARTURE_AT.plusMinutes(1);

    @Autowired private MutableClock clock;
    @Autowired private CarpoolRequestService requestService;
    @Autowired private CarpoolRequestExpireScheduler expireScheduler;
    @Autowired private TestEntityManager em;

    private User host;
    private int userSeq;

    @BeforeEach
    void setUp() {
        clock.set(BEFORE_DEPARTURE);
        host = em.persist(user("방장"));
    }

    @Nested
    @DisplayName("호스트 거절")
    class HostReject {

        @Test
        @DisplayName("reject_reason=HOST 로 남고, 요청자에게 이동 대상 없는 CARPOOL_REQUEST_REJECTED 알림이 1건 간다")
        void rejectsWithNotification() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            User requester = newUser();
            Long requestId = send(requester, carpool);

            requestService.handle(host.getId(), carpool.getId(), requestId, REJECTED);

            flushAndClear();
            assertRequest(requestId, REJECTED, RejectReason.HOST);
            assertThat(notificationsOf(requestId)).singleElement().satisfies(n -> {
                assertThat(n.getRecipient().getId()).isEqualTo(requester.getId());
                assertThat(n.getType()).isEqualTo(NotificationType.CARPOOL_REQUEST_REJECTED);
                assertThat(n.getContent()).isEqualTo("방장님이 동승 요청을 거절했어요");
                assertThat(n.getTargetChatRoom()).isNull();
                assertThat(n.getTargetPost()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("정원 마감 즉시 거절")
    class CapacityFull {

        @Test
        @DisplayName("마지막 자리를 수락하면 남은 PENDING 요청이 모두 REJECTED (CAPACITY_FULL) 가 되고 각자 알림 1건")
        void rejectsRemainingOnLastSeat() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            List<Long> requests = sendFrom(carpool, 5);

            accept(carpool, requests.get(0));
            accept(carpool, requests.get(1));
            flushAndClear();
            assertRequest(requests.get(3), PENDING, null);
            assertRequest(requests.get(4), PENDING, null);

            accept(carpool, requests.get(2));

            flushAndClear();
            assertThat(em.find(Companion.class, carpool.getId()).getCurrentCount()).isEqualTo(4);
            for (Long accepted : requests.subList(0, 3)) {
                assertRequest(accepted, ACCEPTED, null);
                assertThat(notificationsOf(accepted)).singleElement()
                        .extracting(Notification::getType).isEqualTo(NotificationType.CARPOOL_REQUEST_ACCEPTED);
            }
            for (Long left : requests.subList(3, 5)) {
                assertRequest(left, REJECTED, RejectReason.CAPACITY_FULL);
                assertThat(notificationsOf(left)).singleElement().satisfies(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.CARPOOL_REQUEST_REJECTED);
                    assertThat(n.getContent()).isEqualTo("카풀 자리가 모두 찼어요. 다른 카풀을 찾아보세요");
                    assertThat(n.getTargetChatRoom()).isNull();
                    assertThat(n.getTargetPost()).isNull();
                });
            }
        }

        @Test
        @DisplayName("정원이 남는 수락에서는 아무 요청도 거절되지 않는다")
        void keepsPendingWhileSeatsLeft() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            List<Long> requests = sendFrom(carpool, 3);

            accept(carpool, requests.get(0));

            flushAndClear();
            assertRequest(requests.get(1), PENDING, null);
            assertRequest(requests.get(2), PENDING, null);
            assertThat(notificationsOf(requests.get(1))).isEmpty();
            assertThat(notificationsOf(requests.get(2))).isEmpty();
        }

        @Test
        @DisplayName("다른 카풀의 PENDING 요청은 건드리지 않는다")
        void otherCarpoolUntouched() {
            Companion full = persistCarpool(RECRUITING, 3);
            Companion other = persistCarpool(RECRUITING, 1);
            User requester = newUser();
            Long last = send(newUser(), full);
            Long elsewhere = send(requester, other);

            accept(full, last);

            flushAndClear();
            assertRequest(elsewhere, PENDING, null);
        }
    }

    @Nested
    @DisplayName("재신청 제한")
    class RequestLimit {

        @Test
        @DisplayName("호스트 거절(HOST) 3건이면 REQUEST_LIMIT_EXCEEDED")
        void hostRejectionsCount() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            User requester = newUser();
            for (int i = 0; i < 3; i++) {
                Long id = send(requester, carpool);
                requestService.handle(host.getId(), carpool.getId(), id, REJECTED);
            }

            assertThatThrownBy(() -> send(requester, carpool))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CarpoolErrorCode.REQUEST_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("CAPACITY_FULL · DEPARTED 거절은 세지 않는다")
        void systemRejectionsDoNotCount() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            User requester = newUser();
            persistRejected(carpool, requester, RejectReason.HOST);
            persistRejected(carpool, requester, RejectReason.HOST);
            persistRejected(carpool, requester, RejectReason.CAPACITY_FULL);
            persistRejected(carpool, requester, RejectReason.DEPARTED);
            persistRejected(carpool, requester, RejectReason.CAPACITY_FULL);

            Long id = send(requester, carpool);

            assertRequest(id, PENDING, null);
        }
    }

    @Nested
    @DisplayName("출발 경과 sweep")
    class ExpireSweep {

        @Test
        @DisplayName("출발 시각 전 카풀은 대상이 아니다")
        void beforeDepartureUntouched() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            Long id = send(newUser(), carpool);

            clock.set(DEPARTURE_AT);
            expireScheduler.sweep();

            flushAndClear();
            assertRequest(id, PENDING, null);
            assertThat(requestService.findExpireDueIds()).isEmpty();
        }

        @Test
        @DisplayName("출발 경과 카풀의 PENDING 은 REJECTED (DEPARTED) 가 되고 CARPOOL_REQUEST_EXPIRED 알림이 1건 간다")
        void expiresPending() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            User requester = newUser();
            Long pending = send(requester, carpool);
            Long accepted = send(newUser(), carpool);
            accept(carpool, accepted);

            clock.set(AFTER_DEPARTURE);
            expireScheduler.sweep();

            flushAndClear();
            assertRequest(pending, REJECTED, RejectReason.DEPARTED);
            assertRequest(accepted, ACCEPTED, null);
            assertThat(notificationsOf(pending)).singleElement().satisfies(n -> {
                assertThat(n.getRecipient().getId()).isEqualTo(requester.getId());
                assertThat(n.getType()).isEqualTo(NotificationType.CARPOOL_REQUEST_EXPIRED);
                assertThat(n.getContent()).isEqualTo("출발 시간이 지나 동승 요청이 종료됐어요");
                assertThat(n.getTargetChatRoom()).isNull();
                assertThat(n.getTargetPost()).isNull();
            });
        }

        @Test
        @DisplayName("companions.status 가 IN_PROGRESS · COMPLETED 인 카풀도 대상이다")
        void includesStartedCarpools() {
            Companion inProgress = persistCarpool(IN_PROGRESS, 2);
            Companion completed = persistCarpool(COMPLETED, 2);
            Long a = persistPending(inProgress, newUser());
            Long b = persistPending(completed, newUser());

            clock.set(AFTER_DEPARTURE);
            expireScheduler.sweep();

            flushAndClear();
            assertRequest(a, REJECTED, RejectReason.DEPARTED);
            assertRequest(b, REJECTED, RejectReason.DEPARTED);
        }

        @Test
        @DisplayName("두 번 실행해도 알림이 중복되지 않는다")
        void idempotent() {
            Companion carpool = persistCarpool(RECRUITING, 1);
            Long id = send(newUser(), carpool);

            clock.set(AFTER_DEPARTURE);
            expireScheduler.sweep();
            flushAndClear();
            expireScheduler.sweep();

            flushAndClear();
            assertRequest(id, REJECTED, RejectReason.DEPARTED);
            assertThat(notificationsOf(id)).hasSize(1);
        }

        @Test
        @DisplayName("대상 카풀이 101개면 1회 실행에 100개만 처리한다")
        void limitsBatch() {
            for (int i = 0; i < 101; i++) {
                persistPending(persistCarpool(RECRUITING, 1), newUser());
            }
            flushAndClear();

            clock.set(AFTER_DEPARTURE);
            assertThat(requestService.findExpireDueIds()).hasSize(100);
            expireScheduler.sweep();

            flushAndClear();
            assertThat(countByStatus(PENDING)).isEqualTo(1);
            assertThat(countByStatus(REJECTED)).isEqualTo(100);
            assertThat(requestService.findExpireDueIds()).hasSize(1);
        }
    }

    // ---------------------------------------------------------------------

    private Companion persistCarpool(CompanionStatus status, int currentCount) {
        Companion carpool = em.persist(carpool(host, status, currentCount));
        em.persist(participant(carpool, host, OutcomeStatus.PENDING));
        em.persist(ChatRoom.create(carpool));
        em.flush();
        return carpool;
    }

    private User newUser() {
        return em.persistAndFlush(user("요청자" + (++userSeq)));
    }

    private Long send(User requester, Companion carpool) {
        return requestService.send(requester.getId(), carpool.getId(), "같이 가요").id();
    }

    private List<Long> sendFrom(Companion carpool, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(send(newUser(), carpool));
        }
        return ids;
    }

    private void accept(Companion carpool, Long requestId) {
        requestService.handle(host.getId(), carpool.getId(), requestId, ACCEPTED);
    }

    private Long persistPending(Companion carpool, User requester) {
        return em.persistAndFlush(CompanionRequest.send(carpool, requester, "같이 가요")).getId();
    }

    private void persistRejected(Companion carpool, User requester, RejectReason reason) {
        CompanionRequest request = CompanionRequest.send(carpool, requester, "예전 요청");
        request.reject(reason);
        em.persistAndFlush(request);
    }

    private void assertRequest(Long id, CompanionRequestStatus status, RejectReason reason) {
        CompanionRequest request = em.find(CompanionRequest.class, id);
        assertThat(request.getStatus()).isEqualTo(status);
        assertThat(request.getRejectReason()).isEqualTo(reason);
    }

    private List<Notification> notificationsOf(Long requestId) {
        return em.getEntityManager()
                .createQuery("SELECT n FROM Notification n JOIN FETCH n.recipient WHERE n.eventId = :id"
                        + " AND n.type <> :received", Notification.class)
                .setParameter("id", requestId)
                .setParameter("received", NotificationType.CARPOOL_REQUEST_RECEIVED)
                .getResultList();
    }

    private long countByStatus(CompanionRequestStatus status) {
        return em.getEntityManager()
                .createQuery("SELECT COUNT(r) FROM CompanionRequest r WHERE r.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
