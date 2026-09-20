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
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /companion-posts, GET /companion-posts/{companion_id}.
 *
 * [주의할 내용]
 * - capacity 계산: recruit_count(방장 제외)에 +1 해서 Companion.capacity(방장 포함 총원)로
 *   저장했다 — Companion 생성자가 currentCount를 1(방장)로 시작하는 것과 맞춘 것. 확인 필요.
 * - Companion.createRecruiting()은 현재 엔티티의 실제 시그니처(User 타입)를 그대로 썼다
 *   (테크스펙 문서 스니펫은 Long userId를 받는 옛날 버전이라 안 따름 — 예전에 flag한 부분).
 * - participantIds: companion_participants는 companions와 같은 동행 데이터라
 *   companionpost 레포지토리(CompanionPostRepository.findParticipantUserIds)에서 native
 *   query로 직접 조회하도록 고쳤다. left_at IS NULL로 "현재 참여 중"만 거른 건 내 판단이라
 *   맞는지 확인 필요.
 * - isExpired: RECRUITING이 아닐 때 true로 뒤집었다(직전에 확인해준 대로).
 * - joined: 테크스펙 6번 정의(RECRUITING이면서 current_count < capacity) 그대로 썼다 — 이 필드
 *   이름과 "비로그인 허용" 권한 둘 다 테크스펙 원문(동행모집 상세조회 섹션)에 그대로 있는 내용이다.
 */
@Service
@RequiredArgsConstructor
public class CompanionPostService {

    private final CompanionRepository companionRepository;
    private final CompanionPostRepository companionPostRepository;
    private final EntityManager entityManager;

    @Transactional
    public CompanionPostCreateResponse create(Long userId, CompanionPostCreateRequest request) {
        User host = getHostOrThrow(userId); // 401

        validateDepartureAtFuture(request); // 422
        validateOriginDestDifferent(request); // 422
        validateRecruitCount(request); // 422

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
        return new CompanionPostCreateResponse(companion.getId(), null);
    }

    @Transactional(readOnly = true)
    public CompanionPostDetailResponse find(Long companionId) {
        Companion companion = companionRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(CompanionPostErrorCode.COMPANION_POST_NOT_FOUND)); // 404

        if (companion.getStatus() == CompanionStatus.CANCELED) {
            throw new BusinessException(CompanionPostErrorCode.COMPANION_POST_CANCELED); // 410
        }

        List<Long> participantIds = companionPostRepository.findParticipantUserIds(companionId);

        boolean isExpired = companion.getStatus() != CompanionStatus.RECRUITING;
        boolean joined = companion.getStatus() == CompanionStatus.RECRUITING
                && companion.getCurrentCount() < companion.getCapacity();

        return CompanionPostDetailResponse.of(companion, participantIds, isExpired, joined);
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

    private User getHostOrThrow(Long userId) {
        try {
            User host = entityManager.getReference(User.class, userId);
            if (host.isWithdrawn()) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
            }
            return host;
        } catch (EntityNotFoundException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
