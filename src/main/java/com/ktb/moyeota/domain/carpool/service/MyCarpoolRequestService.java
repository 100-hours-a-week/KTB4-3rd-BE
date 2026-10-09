package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import com.ktb.moyeota.domain.carpool.model.CarpoolRequestCursor;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequestItem;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequests;
import com.ktb.moyeota.domain.carpool.model.MyRequestStatus;
import com.ktb.moyeota.domain.carpool.model.RequestDirection;
import com.ktb.moyeota.domain.carpool.repository.ActiveChatRoomProjection;
import com.ktb.moyeota.domain.carpool.repository.CarpoolParticipantRepository;
import com.ktb.moyeota.domain.carpool.repository.CompanionRequestRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyCarpoolRequestService {

    private static final int PAGE_SIZE = 10;

    private final CompanionRequestRepository companionRequestRepository;
    private final CarpoolParticipantRepository carpoolParticipantRepository;
    private final UserRepository userRepository;
    private final CarpoolRequestCursorCodec cursorCodec;
    private final ImageUrlResolver imageUrlResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyCarpoolRequests find(Long userId, RequestDirection direction, String cursor) {
        userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        Long cursorId = cursorCodec.decode(cursor).map(CarpoolRequestCursor::id).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        PageRequest page = PageRequest.of(0, PAGE_SIZE + 1);

        List<CompanionRequest> rows = direction == RequestDirection.SENT
                ? companionRequestRepository.findSent(userId, cursorId, page)
                : companionRequestRepository.findReceivable(userId, now, cursorId, page);
        List<CompanionRequest> pageRows = rows.stream().limit(PAGE_SIZE).toList();

        Map<Long, Long> chatRoomIds = direction == RequestDirection.SENT
                ? findChatRoomsOfAccepted(userId, pageRows)
                : Map.of();
        List<MyCarpoolRequestItem> items = pageRows.stream()
                .map(request -> toItem(request, direction, now, chatRoomIds))
                .toList();
        String nextCursor = rows.size() > PAGE_SIZE
                ? cursorCodec.encode(new CarpoolRequestCursor(pageRows.getLast().getId()))
                : null;
        return new MyCarpoolRequests(direction, items, nextCursor);
    }

    private Map<Long, Long> findChatRoomsOfAccepted(Long userId, List<CompanionRequest> requests) {
        List<Long> acceptedCarpoolIds = requests.stream()
                .filter(request -> request.getStatus() == CompanionRequestStatus.ACCEPTED)
                .map(request -> request.getCompanion().getId())
                .toList();
        if (acceptedCarpoolIds.isEmpty()) {
            return Map.of();
        }
        return carpoolParticipantRepository.findActiveChatRooms(userId, acceptedCarpoolIds).stream()
                .collect(Collectors.toMap(
                        ActiveChatRoomProjection::getCarpoolId, ActiveChatRoomProjection::getChatRoomId));
    }

    private MyCarpoolRequestItem toItem(
            CompanionRequest request, RequestDirection direction, LocalDateTime now, Map<Long, Long> chatRoomIds) {
        Companion carpool = request.getCompanion();
        User counterpart = direction == RequestDirection.SENT ? carpool.getHost() : request.getRequester();
        Long chatRoomId = request.getStatus() == CompanionRequestStatus.ACCEPTED
                ? chatRoomIds.get(carpool.getId())
                : null;
        return new MyCarpoolRequestItem(
                request.getId(),
                carpool.getId(),
                MyRequestStatus.of(request.getStatus(), carpool, now),
                request.getContent(),
                new Member(counterpart.getId(), counterpart.getName(), imageUrlResolver.toUrl(counterpart.getProfileImageUrl())),
                carpool.getOriginName(),
                carpool.getDestName(),
                carpool.getDepartureAt(),
                chatRoomId,
                request.getCreatedAt());
    }
}
