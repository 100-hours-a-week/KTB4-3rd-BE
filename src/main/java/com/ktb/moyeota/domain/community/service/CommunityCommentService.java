package com.ktb.moyeota.domain.community.service;

import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateResponse.Author;
import com.ktb.moyeota.domain.community.dto.CommunityCommentItem;
import com.ktb.moyeota.domain.community.dto.CommunityCommentListResponse;
import com.ktb.moyeota.domain.community.entity.CommunityComment;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.community.exception.CommunityErrorCode;
import com.ktb.moyeota.domain.community.repository.CommunityCommentRepository;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityCommentService {

    private static final int PAGE_SIZE = 10;

    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostRepository communityPostRepository;
    private final EntityManager entityManager;

    @Transactional
    public CommunityCommentCreateResponse create(Long userId, Long postId, CommunityCommentCreateRequest request) {
        User author = entityManager.getReference(User.class, userId);
        if (author.isWithdrawn()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND)); // 404

        if (post.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_COMMENT_POST_DELETED); // 409
        }

        CommunityComment comment = CommunityComment.create(post, author, request.content());
        communityCommentRepository.save(comment);
        communityPostRepository.increaseCommentCount(postId);

        return new CommunityCommentCreateResponse(
                comment.getId(),
                new Author(author.getNickname()),
                comment.getContent(),
                comment.getCreatedAt(),
                post.getCommentCount() + 1
        );
    }

    @Transactional(readOnly = true)
    public CommunityCommentListResponse findAll(Long postId, Long cursor) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND)); // 404

        if (post.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_POST_DELETED); // 410 (댓글 작성 409와 다름)
        }

        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1);
        List<CommunityComment> fetched = communityCommentRepository.findByPostIdWithAuthor(postId, cursor, pageable);

        boolean hasNext = fetched.size() > PAGE_SIZE;
        List<CommunityComment> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        List<CommunityCommentItem> items = page.stream()
                .map(CommunityCommentItem::from)
                .toList();

        return new CommunityCommentListResponse(items, nextCursor);
    }
}
