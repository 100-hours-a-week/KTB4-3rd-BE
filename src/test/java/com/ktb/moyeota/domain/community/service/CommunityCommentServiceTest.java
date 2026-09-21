package com.ktb.moyeota.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityCommentListResponse;
import com.ktb.moyeota.domain.community.entity.CommunityComment;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.community.exception.CommunityErrorCode;
import com.ktb.moyeota.domain.community.repository.CommunityCommentRepository;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CommunityCommentService 단위 테스트.
 *
 * [참고] 댓글 작성 API는 "게시글 삭제됨"이 409, 댓글 목록 조회 API는 410으로 서로 다른 상태
 * 코드를 쓴다는 테크스펙 규칙(주석 참고)을 각각 별도로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CommunityCommentServiceTest {

    private static final int PAGE_SIZE = 10;
    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private EntityManager entityManager;

    private CommunityCommentService service;

    @BeforeEach
    void setUp() {
        service = new CommunityCommentService(communityCommentRepository, communityPostRepository, entityManager);
    }

    private User activeUser() {
        return User.register("우림", "rain", Gender.FEMALE, true, null);
    }

    private User withdrawnUser() {
        User user = User.register("탈퇴자", "withdrawn", Gender.FEMALE, true, null);
        user.withdraw(LocalDateTime.now());
        return user;
    }

    private CommunityPost activePost() {
        CommunityPost post = mock(CommunityPost.class);
        given(post.isDeleted()).willReturn(false);
        return post;
    }

    private CommunityPost deletedPost() {
        CommunityPost post = mock(CommunityPost.class);
        given(post.isDeleted()).willReturn(true);
        return post;
    }

    @Nested
    @DisplayName("댓글 작성")
    class Create {

        private final CommunityCommentCreateRequest request = new CommunityCommentCreateRequest("저도 궁금해요!");

        @Test
        @DisplayName("정상 유저/게시글이면 댓글을 저장하고 댓글 수를 +1한다")
        void createsComment() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(activePost()));

            CommunityCommentCreateResponse response = service.create(USER_ID, POST_ID, request);

            assertThat(response).isNotNull();
            verify(communityCommentRepository).save(any(CommunityComment.class));
            verify(communityPostRepository).increaseCommentCount(POST_ID);
        }

        @Test
        @DisplayName("탈퇴한 유저면 401을 던지고 댓글을 저장하지 않는다")
        void rejectsWithdrawnUser() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(withdrawnUser());

            assertThatThrownBy(() -> service.create(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.UNAUTHORIZED);

            verify(communityCommentRepository, never()).save(any());
        }

        @Test
        @DisplayName("게시글이 없으면 404를 던진다")
        void throwsNotFoundWhenPostMissing() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("삭제된 게시글이면 409를 던진다(목록 조회의 410과 다름)")
        void throwsConflictWhenPostDeleted() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(deletedPost()));

            assertThatThrownBy(() -> service.create(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommunityErrorCode.COMMUNITY_COMMENT_POST_DELETED);

            verify(communityCommentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("댓글 목록 조회")
    class FindAll {

        @Test
        @DisplayName("게시글이 없으면 404를 던진다")
        void throwsNotFoundWhenPostMissing() {
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.findAll(POST_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("삭제된 게시글이면 410을 던진다(작성의 409와 다름)")
        void throwsGoneWhenPostDeleted() {
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(deletedPost()));

            assertThatThrownBy(() -> service.findAll(POST_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommunityErrorCode.COMMUNITY_POST_DELETED);
        }

        private CommunityComment commentWithId(Long id) {
            CommunityComment comment = mock(CommunityComment.class);
            given(comment.getId()).willReturn(id);
            given(comment.getContent()).willReturn("댓글 " + id);
            given(comment.getCreatedAt()).willReturn(LocalDateTime.now());
            given(comment.getAuthor()).willReturn(User.register("작성자" + id, "author" + id, Gender.FEMALE, true, null));
            return comment;
        }

        @Test
        @DisplayName("PAGE_SIZE보다 많이 조회되면 10건만 반환하고 nextCursor를 채운다")
        void returnsNextCursorWhenMoreThanPageSize() {
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(activePost()));
            List<CommunityComment> fetched = new ArrayList<>();
            for (long id = PAGE_SIZE + 1; id >= 1; id--) {
                fetched.add(commentWithId(id)); // 11건, id 내림차순
            }
            given(communityCommentRepository.findByPostIdWithAuthor(eq(POST_ID), any(), any())).willReturn(fetched);

            CommunityCommentListResponse response = service.findAll(POST_ID, null);

            assertThat(response.items()).hasSize(PAGE_SIZE);
            assertThat(response.nextCursor()).isEqualTo(2L); // 10건째(마지막) 댓글의 id
        }

        @Test
        @DisplayName("PAGE_SIZE 이하로 조회되면 nextCursor가 없다")
        void returnsNoCursorWhenExactlyPageSizeOrLess() {
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(activePost()));
            List<CommunityComment> fetched = new ArrayList<>();
            for (long id = 5; id >= 1; id--) {
                fetched.add(commentWithId(id)); // 5건
            }
            given(communityCommentRepository.findByPostIdWithAuthor(eq(POST_ID), any(), any())).willReturn(fetched);

            CommunityCommentListResponse response = service.findAll(POST_ID, null);

            assertThat(response.items()).hasSize(5);
            assertThat(response.nextCursor()).isNull();
        }
    }
}
