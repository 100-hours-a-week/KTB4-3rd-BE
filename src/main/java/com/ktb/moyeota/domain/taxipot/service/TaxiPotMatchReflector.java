package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.AlreadyApplied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Applied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Rejected;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxiPotMatchReflector {

    private final TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;
    private final TaxiPotRepository taxiPotRepository;
    private final TaxiPotParticipantRepository taxiPotParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public MatchApplyOutcome apply(MatchResult result) {
        return result.opensPot() ? applyOpen(result) : applyJoin(result);
    }

    private MatchApplyOutcome applyOpen(MatchResult result) {
        List<TaxiPotMatchRequest> requests = lockRequests(result);
        if (requests.size() != result.requests().size()) {
            return reject(result, RejectReason.REQUEST_NOT_FOUND);
        }
        Optional<Long> applied = appliedTaxiPotId(requests);
        if (applied.isPresent()) {
            return new AlreadyApplied(applied.get());
        }
        if (!allMatching(requests)) {
            return reject(result, RejectReason.REQUEST_NOT_MATCHING);
        }
        if (isDeparted(result)) {
            return reject(result, RejectReason.DEPARTED);
        }

        TaxiPotMatchRequest origin = find(requests, result.originAnchorRequestId());
        TaxiPotMatchRequest dest = find(requests, result.destAnchorRequestId());
        User host = requests.getFirst().getUser();
        Companion taxiPot = taxiPotRepository.save(Companion.openTaxiPot(host,
                origin.getOriginName(), origin.getOriginLat(), origin.getOriginLng(),
                dest.getDestName(), dest.getDestLat(), dest.getDestLng(), result.departureAt()));
        taxiPotParticipantRepository.save(CompanionParticipant.join(taxiPot, host));
        for (TaxiPotMatchRequest request : requests.subList(1, requests.size())) {
            taxiPotParticipantRepository.save(taxiPot.join(request.getUser(), null));
        }
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.create(taxiPot));
        matchAll(requests, taxiPot, chatRoom);
        return new Applied(taxiPot.getId());
    }

    private MatchApplyOutcome applyJoin(MatchResult result) {
        Long potRequestId = Long.valueOf(result.potKey().split(",")[0]);
        Optional<Long> taxiPotId = taxiPotMatchRequestRepository.findCompanionIdById(potRequestId);
        if (taxiPotId.isEmpty()) {
            return reject(result, RejectReason.POT_NOT_FOUND);
        }
        Optional<Companion> recruiting = taxiPotRepository.findRecruitingTaxiPotForUpdate(taxiPotId.get());
        List<TaxiPotMatchRequest> requests = lockRequests(result);
        if (requests.size() != result.requests().size()) {
            return reject(result, RejectReason.REQUEST_NOT_FOUND);
        }
        if (requests.stream().allMatch(request -> request.isMatchedTo(taxiPotId.get()))) {
            return new AlreadyApplied(taxiPotId.get());
        }
        if (!allMatching(requests)) {
            return reject(result, RejectReason.REQUEST_NOT_MATCHING);
        }
        if (recruiting.isEmpty()) {
            return reject(result, RejectReason.POT_NOT_RECRUITING);
        }
        Companion taxiPot = recruiting.get();
        if (taxiPot.getCurrentCount() + requests.size() > taxiPot.getCapacity()) {
            return reject(result, RejectReason.POT_FULL);
        }
        if (isDeparted(result)) {
            return reject(result, RejectReason.DEPARTED);
        }

        for (TaxiPotMatchRequest request : requests) {
            User user = request.getUser();
            CompanionParticipant previous = taxiPotParticipantRepository
                    .findByCompanionIdAndUserId(taxiPot.getId(), user.getId())
                    .orElse(null);
            taxiPotParticipantRepository.save(taxiPot.join(user, previous));
        }
        ChatRoom chatRoom = chatRoomRepository.findByCompanionId(taxiPot.getId())
                .orElseThrow(() -> new IllegalStateException("택시팟에 채팅방이 없다: " + taxiPot.getId()));
        matchAll(requests, taxiPot, chatRoom);
        return new Applied(taxiPot.getId());
    }

    private List<TaxiPotMatchRequest> lockRequests(MatchResult result) {
        return taxiPotMatchRequestRepository.findAllByIdInForUpdate(
                result.requests().stream().map(MatchRequest::requestId).toList());
    }

    private void matchAll(List<TaxiPotMatchRequest> requests, Companion taxiPot, ChatRoom chatRoom) {
        for (TaxiPotMatchRequest request : requests) {
            request.matchTo(taxiPot);
            chatSystemMessageService.enter(chatRoom, request.getUser());
        }
        eventPublisher.publishEvent(new TaxiPotMatchedEvent(taxiPot.getId(), chatRoom.getId(),
                requests.stream().map(request -> request.getUser().getId()).toList()));
    }

    private boolean isDeparted(MatchResult result) {
        return result.departureAt().isBefore(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
    }

    private MatchApplyOutcome reject(MatchResult result, RejectReason reason) {
        log.info("매칭 결과를 반영하지 않는다. resultId={}, reason={}", result.resultId(), reason);
        return new Rejected(reason);
    }

    private static Optional<Long> appliedTaxiPotId(List<TaxiPotMatchRequest> requests) {
        Companion first = requests.getFirst().getCompanion();
        if (first == null || !requests.stream().allMatch(request -> request.isMatchedTo(first.getId()))) {
            return Optional.empty();
        }
        return Optional.of(first.getId());
    }

    private static boolean allMatching(List<TaxiPotMatchRequest> requests) {
        return requests.stream().allMatch(TaxiPotMatchRequest::isMatching);
    }

    private static TaxiPotMatchRequest find(List<TaxiPotMatchRequest> requests, Long requestId) {
        return requests.stream()
                .filter(request -> request.getId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("기준 요청이 결과에 없다: " + requestId));
    }
}
