package com.ktb.moyeota.domain.companionpost.service;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.entity.TransportType;
import com.ktb.moyeota.domain.companion.repository.CompanionRepository;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateRequest;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateResponse;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostDetailResponse;
import com.ktb.moyeota.domain.companionpost.exception.CompanionPostErrorCode;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionPostService {

    private final CompanionRepository companionRepository;
    private final CompanionPostRepository companionPostRepository;
    private final EntityManager entityManager;

    @Transactional
    public CompanionPostCreateResponse create(Long userId, CompanionPostCreateRequest request) {
        User host = entityManager.getReference(User.class, userId);
        if (host.isWithdrawn()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        validateDepartureAtFuture(request);
        validateOriginDestDifferent(request);
        validateRecruitCount(request);

        int capacity = request.recruitCount() + 1; // 방장 포함 총원
        Companion companion = Companion.createRecruiting(
                host,
                request.transportType(),
                request.content(),
                request.originName(),
                request.originLat(),
                request.originLng(),
                request.destName(),
                request.destLat(),
                request.destLng(),
                request.departureAt(),
                capacity);
        companionRepository.save(companion);

        // TODO: [채팅 도메인 연동] chat_rooms insert는 Chat 도메인 범위라 구현하지 않음 — chatRoomId는 null.
        return new CompanionPostCreateResponse(companion.getId());
    }

    @Transactional(readOnly = true)
    public CompanionPostDetailResponse find(Long userId, Long companionId) {

        Companion companion = companionRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(CompanionPostErrorCode.COMPANION_POST_NOT_FOUND));

        if (companion.getStatus() == CompanionStatus.CANCELED) {
            throw new BusinessException(CompanionPostErrorCode.COMPANION_POST_CANCELED);
        }

        boolean isExpired = companion.getDepartureAt().isBefore(LocalDateTime.now());
        boolean isFull = companion.getCurrentCount() >= companion.getCapacity();
        boolean joined = companion.getHost().getId().equals(userId);
        String authorNickname = companion.getHost().getNickname();

        return CompanionPostDetailResponse.of(companion, isExpired, isFull, joined, authorNickname);
    }

    private void validateDepartureAtFuture(CompanionPostCreateRequest request) {
        if (!request.departureAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(CompanionPostErrorCode.COMPANION_POST_DEPARTURE_AT_PAST);
        }
    }

    private void validateOriginDestDifferent(CompanionPostCreateRequest request) {
        boolean samePoint = request.originLat().compareTo(request.destLat()) == 0
                && request.originLng().compareTo(request.destLng()) == 0;
        if (samePoint) {
            throw new BusinessException(CompanionPostErrorCode.COMPANION_POST_ORIGIN_DEST_SAME);
        }
    }

    private void validateRecruitCount(CompanionPostCreateRequest request) {
        int max = maxRecruitCountFor(request.transportType());
        int recruitCount = request.recruitCount();
        if (recruitCount < 0 || recruitCount > max) {
            throw new BusinessException(CompanionPostErrorCode.COMPANION_POST_RECRUIT_COUNT_OUT_OF_RANGE);
        }
    }

    private int maxRecruitCountFor(TransportType transportType) {
        return switch (transportType) {
            case TAXI, OWNED_CAR -> 3;
            case SUBWAY, BUS -> 9;
        };
    }
}
