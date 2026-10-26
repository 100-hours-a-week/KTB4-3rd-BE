package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchingRequest;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.OpenPot;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Applied;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@RecordApplicationEvents
@Import({TaxiPotMatchReflector.class, ChatSystemMessageService.class, NotificationCommandService.class,
        TaxiPotMatchReflectorTestSupport.FixedClock.class})
abstract class TaxiPotMatchReflectorTestSupport {

    protected static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    protected TaxiPotMatchReflector reflector;

    @Autowired
    protected TestEntityManager entityManager;

    @Autowired
    protected ApplicationEvents events;

    private int sequence;

    protected List<TaxiPotMatchRequest> requests(int count) {
        List<TaxiPotMatchRequest> saved = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sequence++;
            User user = entityManager.persist(bankAccountHolder("요청자" + sequence));
            saved.add(entityManager.persist(
                    matchingRequest(user, "출발지" + sequence, "도착지" + sequence, DEPARTURE_AT)));
        }
        entityManager.flush();
        return saved;
    }

    protected Long appliedPotId(MatchApplyOutcome outcome) {
        if (outcome instanceof Applied applied) {
            return applied.taxiPotId();
        }
        throw new AssertionError("반영되지 않았다: " + outcome);
    }

    protected MatchResult openResult(List<TaxiPotMatchRequest> requests) {
        Long anchor = requests.getFirst().getId();
        return MatchResult.openPot(matchRequests(requests), anchor, anchor, DEPARTURE_AT);
    }

    protected MatchResult joinResult(List<TaxiPotMatchRequest> founders, List<TaxiPotMatchRequest> joiners) {
        String potKey = openResult(founders).potKey();
        return MatchResult.joinPot(new OpenPot(potKey, DEPARTURE_AT, matchRequests(founders)), matchRequests(joiners));
    }

    protected static List<MatchRequest> matchRequests(List<TaxiPotMatchRequest> requests) {
        return requests.stream().map(request -> matchRequest(request.getId())).toList();
    }

    protected static List<Long> userIds(List<TaxiPotMatchRequest> requests) {
        return requests.stream().map(request -> request.getUser().getId()).toList();
    }

    protected void changeStatus(TaxiPotMatchRequest request, TaxiPotMatchRequestStatus status) {
        ReflectionTestUtils.setField(request, "status", status);
        entityManager.flush();
    }

    protected Companion reloadPot(Long potId) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, potId);
    }

    protected List<TaxiPotMatchRequest> reloadRequests(List<TaxiPotMatchRequest> requests) {
        entityManager.flush();
        entityManager.clear();
        return requests.stream().map(request -> entityManager.find(TaxiPotMatchRequest.class, request.getId())).toList();
    }

    protected long companionCount() {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT count(c) FROM Companion c", Long.class)
                .getSingleResult();
    }

    protected Long chatRoomId(Long potId) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT r FROM ChatRoom r WHERE r.companion.id = :potId", ChatRoom.class)
                .setParameter("potId", potId)
                .getSingleResult()
                .getId();
    }

    protected List<Long> participantUserIds(Long potId) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :potId",
                        CompanionParticipant.class)
                .setParameter("potId", potId)
                .getResultList().stream()
                .map(p -> p.getUser().getId())
                .toList();
    }

    protected List<Long> joinMessageSenderIds(Long potId) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.companion.id = :potId"
                        + " AND m.messageType = :type ORDER BY m.id", Message.class)
                .setParameter("potId", potId)
                .setParameter("type", MessageType.SYSTEM_JOIN)
                .getResultList().stream()
                .map(m -> m.getSender().getId())
                .toList();
    }

    protected List<Notification> matchedNotifications() {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT n FROM Notification n WHERE n.type = :type ORDER BY n.id", Notification.class)
                .setParameter("type", NotificationType.MATCHING_COMPLETED)
                .getResultList();
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
