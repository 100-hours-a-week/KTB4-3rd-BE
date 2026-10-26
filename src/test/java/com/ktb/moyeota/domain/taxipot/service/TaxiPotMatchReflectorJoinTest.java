package com.ktb.moyeota.domain.taxipot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.AlreadyApplied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Applied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Rejected;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TaxiPotMatchReflectorJoinTest extends TaxiPotMatchReflectorTestSupport {

    @Test
    @DisplayName("합류 결과를 반영하면 기존 팟 인원이 늘고 합류 요청이 그 팟으로 매칭된다")
    void joinsOpenPot() {
        List<TaxiPotMatchRequest> founders = requests(2);
        Long potId = appliedPotId(reflector.apply(openResult(founders)));
        List<TaxiPotMatchRequest> joiners = requests(1);

        MatchApplyOutcome outcome = reflector.apply(joinResult(founders, joiners));

        assertThat(outcome).isEqualTo(new Applied(potId));
        assertThat(reloadPot(potId).getCurrentCount()).isEqualTo(3);
        assertThat(participantUserIds(potId)).contains(joiners.getFirst().getUser().getId());
        assertThat(joinMessageSenderIds(potId)).endsWith(joiners.getFirst().getUser().getId());
        assertThat(reloadRequests(joiners)).singleElement().satisfies(request -> {
            assertThat(request.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHED);
            assertThat(request.getCompanion().getId()).isEqualTo(potId);
        });
    }

    @Test
    @DisplayName("같은 합류 결과를 두 번 반영하면 한 번만 합류하고 두 번째는 이미 반영됨이다")
    void joinTwiceJoinsOnce() {
        List<TaxiPotMatchRequest> founders = requests(2);
        Long potId = appliedPotId(reflector.apply(openResult(founders)));
        MatchResult join = joinResult(founders, requests(1));

        reflector.apply(join);
        entityManager.flush();
        entityManager.clear();
        MatchApplyOutcome second = reflector.apply(join);

        assertThat(second).isEqualTo(new AlreadyApplied(potId));
        assertThat(reloadPot(potId).getCurrentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("합류하면 정원을 넘는 결과는 거절한다")
    void rejectsJoinOverCapacity() {
        List<TaxiPotMatchRequest> founders = requests(3);
        Long potId = appliedPotId(reflector.apply(openResult(founders)));
        List<TaxiPotMatchRequest> joiners = requests(2);

        MatchApplyOutcome outcome = reflector.apply(joinResult(founders, joiners));

        assertThat(outcome).isEqualTo(new Rejected(RejectReason.POT_FULL));
        assertThat(reloadPot(potId).getCurrentCount()).isEqualTo(3);
        assertThat(reloadRequests(joiners)).extracting(TaxiPotMatchRequest::getStatus)
                .containsOnly(TaxiPotMatchRequestStatus.MATCHING);
    }

    @Test
    @DisplayName("운행을 시작한 팟에 합류하는 결과는 거절한다")
    void rejectsJoinToStartedPot() {
        List<TaxiPotMatchRequest> founders = requests(2);
        Long potId = appliedPotId(reflector.apply(openResult(founders)));
        ReflectionTestUtils.setField(entityManager.find(Companion.class, potId), "status", CompanionStatus.IN_PROGRESS);
        entityManager.flush();
        List<TaxiPotMatchRequest> joiners = requests(1);

        MatchApplyOutcome outcome = reflector.apply(joinResult(founders, joiners));

        assertThat(outcome).isEqualTo(new Rejected(RejectReason.POT_NOT_RECRUITING));
        assertThat(reloadPot(potId).getCurrentCount()).isEqualTo(2);
        assertThat(reloadRequests(joiners).getFirst().getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
    }

    @Test
    @DisplayName("합류를 반영하면 새로 들어온 사람에게만 매칭 완료 알림이 생긴다")
    void notifiesOnlyJoiners() {
        List<TaxiPotMatchRequest> founders = requests(2);
        reflector.apply(openResult(founders));
        List<TaxiPotMatchRequest> joiners = requests(1);

        reflector.apply(joinResult(founders, joiners));

        assertThat(matchedNotifications()).extracting(notification -> notification.getRecipient().getId())
                .containsExactly(founders.get(0).getUser().getId(), founders.get(1).getUser().getId(),
                        joiners.getFirst().getUser().getId());
    }

    @Test
    @DisplayName("반영하면 팟·채팅방·묶인 사용자를 담은 매칭 완료 이벤트를 낸다")
    void publishesMatchedEvent() {
        List<TaxiPotMatchRequest> founders = requests(2);
        Long potId = appliedPotId(reflector.apply(openResult(founders)));
        List<TaxiPotMatchRequest> joiners = requests(1);
        reflector.apply(joinResult(founders, joiners));

        Long chatRoomId = chatRoomId(potId);
        assertThat(events.stream(TaxiPotMatchedEvent.class)).containsExactly(
                new TaxiPotMatchedEvent(potId, chatRoomId, userIds(founders)),
                new TaxiPotMatchedEvent(potId, chatRoomId, userIds(joiners)));
    }
}
