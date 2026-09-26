package com.ktb.moyeota.domain.companionpost.service;

import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionPostService {

    private final CompanionPostRepository companionPostRepository;
    private final ChatRoomService chatRoomService;
    private final EntityManager entityManager;

    @Transactional
    public CompanionPostCreateResponse create(Long userId, CompanionPostCreateRequest request) {
        User host = entityManager.getReference(User.class, userId);
        if (host.isWithdrawn()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        int capacity = request.recruitCount() + 1;
        Companion companion = createCompanionPost(host, request, capacity);

        Optional.of(companion).filter(Companion::isDepartureAtFuture)
                .orElseThrow(() -> new BusinessException(CompanionPostErrorCode.COMPANION_POST_DEPARTURE_AT_PAST));
        Optional.of(companion).filter(Companion::isOriginDestDifferent)
                .orElseThrow(() -> new BusinessException(CompanionPostErrorCode.COMPANION_POST_ORIGIN_DEST_SAME));
        Optional.of(companion).filter(Companion::isCapacityValid)
                .orElseThrow(() -> new BusinessException(CompanionPostErrorCode.COMPANION_POST_RECRUIT_COUNT_OUT_OF_RANGE));

        companionPostRepository.save(companion);

        chatRoomService.createChatRoomForCompanion(companion, host);

        return new CompanionPostCreateResponse(companion.getId());
    }

    @Transactional(readOnly = true)
    public CompanionPostDetailResponse find(Long userId, Long companionId) {

        Companion companion = companionPostRepository.findCompanionPostById(companionId)
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

    private Companion createCompanionPost(User host, CompanionPostCreateRequest request, int capacity) {
        return Companion.createCompanionPost(host, CompanionKind.COMPANION, request.transportType(), request.content(),
                request.originName(), request.originLat(), request.originLng(),
                request.destName(), request.destLat(), request.destLng(),
                request.departureAt(), capacity);
    }
}
