package com.ktb.moyeota.domain.community.service;

import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateResponse;
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
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /community-posts/{post_id}/comments, GET /community-posts/{post_id}/comments.
 *
 * [주의할 내용]
 * - getAuthorOrThrow()는 CommunityPostService에 있는 것과 똑같은 코드다. 도메인은 같으니
 *   공통 헬퍼로 뽑아도 되지만, 지금 범위(서비스 코드 작성)를 벗어나는 리팩토링이라 안 건드리고
 *   일단 중복 그대로 뒀다 — 필요하면 말해줘.
 * - CommunityCommentRepository.findByPostId()는 author를 JOIN FETCH하지 않는데(지난번에
 *   얘기한 N+1 이슈), CommunityCommentItem.from()이 지금 author 필드를 아예 안 쓰고 있어서
 *   (TODO로 남겨둔 상태) 당장은 N+1이 실제로 발생하지 않는다. author 표시 설계가 정해지면
 *   그때 JOIN FETCH를 넣는 걸로 판단해서 지금은 리포지토리도 안 건드렸다.
 * - 7번 [알림 도메인 연동 TODO](notifications insert)는 Notification 도메인 범위라 구현하지 않음.
 */
@Service
@RequiredArgsConstructor
public class CommunityCommentService {

    private static final int PAGE_SIZE = 10;

    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostRepository communityPostRepository;
    private final EntityManager entityManager;

    @Transactional
    public CommunityCommentCreateResponse create(Long userId, Long postId, CommunityCommentCreateRequest request) {
        User author = getAuthorOrThrow(userId); // 401

        CommunityPost post = communityPostRepository.findActiveById(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND)); // 404

        if (post.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_COMMENT_POST_DELETED); // 409
        }

        CommunityComment comment = CommunityComment.create(post, author, request.content());
        communityCommentRepository.save(comment);
        communityPostRepository.increaseCommentCount(postId);

        // TODO: [알림 도메인 연동] notifications(COMMENT_CREATED) insert는 Notification 도메인
        // 개발 후 같은 트랜잭션 내에서 처리 — 이 문서(Community) 범위 밖이라 구현하지 않음.
        return new CommunityCommentCreateResponse(comment.getId());
    }

    @Transactional(readOnly = true)
    public CommunityCommentListResponse findAll(Long postId, Long cursor) {
        CommunityPost post = communityPostRepository.findActiveById(postId)
                .orElseThrow(() -> new BusinessException(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND)); // 404

        if (post.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_POST_DELETED); // 410 (댓글 작성 409와 다름)
        }

        Pageable pageable = PageRequest.of(0, PAGE_SIZE + 1);
        List<CommunityComment> fetched = communityCommentRepository.findByPostId(postId, cursor, pageable);

        boolean hasNext = fetched.size() > PAGE_SIZE;
        List<CommunityComment> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        List<CommunityCommentItem> items = page.stream()
                .map(CommunityCommentItem::from)
                .toList();

        return new CommunityCommentListResponse(items, nextCursor);
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
