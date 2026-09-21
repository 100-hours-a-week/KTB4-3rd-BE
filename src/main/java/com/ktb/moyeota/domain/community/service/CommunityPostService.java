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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityPostRepository communityPostRepository;
    private final EntityManager entityManager;

    @Transactional
    public CommunityPostCreateResponse create(Long userId, CommunityPostCreateRequest request) {
        User author = entityManager.getReference(User.class, userId);
        if (author.isWithdrawn()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        CommunityPost post = CommunityPost.create(
                author, request.title(), request.content(), request.lat(), request.lng());
        communityPostRepository.save(post);

        return new CommunityPostCreateResponse(post.getId());
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponse find(Long postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_POST_DELETED);
        }

        return CommunityPostDetailResponse.from(post);
    }
}
