package com.ktb.moyeota.domain.community.service;

import com.ktb.moyeota.domain.community.dto.CommunityPostCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityPostCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityPostDetailResponse;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.community.exception.CommunityErrorCode;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /community-posts, GET /community-posts/{post_id}.
 *
 * [주의할 내용] 3번 "회원 엔티티를 이용하여 사용자 인증 - 401" 단계는 domain/user에 Repository가
 * 없어서(User 도메인 소관, 이번 범위 밖) EntityManager.getReference()로 프록시만 얻어 접근 시점에
 * 존재/탈퇴 여부를 확인하는 방식으로 구현했다. 실제 인증(토큰 검증) 자체는 Auth 인프라
 * (global/security, 현재 비어 있음)가 없어서 여기서 하지 않는다 — userId가 이미 검증됐다고
 * 가정하고, "그 userId의 회원이 실제로 존재하고 탈퇴하지 않았는지"만 이 메서드가 확인한다.ㅂㅂ
 */
@Service
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityPostRepository communityPostRepository;
    private final EntityManager entityManager;

    @Transactional
    public CommunityPostCreateResponse create(Long userId, CommunityPostCreateRequest request) {
        User author = getAuthorOrThrow(userId);

        CommunityPost post = CommunityPost.create(
                author, request.title(), request.content(), request.lat(), request.lng());
        communityPostRepository.save(post);

        // TODO: Chat 도메인 개발 전까지 chatRoomId는 채우지 않는다(null).
        return new CommunityPostCreateResponse(post.getId(), null);
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponse find(Long postId) {
        CommunityPost post = communityPostRepository.findActiveById(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_POST_DELETED);
        }

        return CommunityPostDetailResponse.from(post);
    }

    private User getAuthorOrThrow(Long userId) {
        try {
            User author = entityManager.getReference(User.class, userId);
            if (author.isWithdrawn()) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
            }
            return author;
        } catch (EntityNotFoundException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
