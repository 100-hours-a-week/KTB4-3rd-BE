package com.ktb.moyeota.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.community.dto.CommunityPostCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityPostCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityPostDetailResponse;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.community.exception.CommunityErrorCode;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CommunityPostService 단위 테스트.
 *
 * [참고] EntityManager.getReference()/Repository는 Mockito로 모킹한다 — 이 테스트는 DB 접근
 * 없이 Service의 분기(정상/탈퇴 회원/게시글 없음/삭제됨)와 DTO 매핑만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private EntityManager entityManager;

    private CommunityPostService service;

    @BeforeEach
    void setUp() {
        service = new CommunityPostService(communityPostRepository, entityManager);
    }

    private User activeUser() {
        return User.register("우림", "rain", Gender.FEMALE, true, null);
    }

    private User withdrawnUser() {
        User user = User.register("탈퇴자", "withdrawn", Gender.FEMALE, true, null);
        user.withdraw(LocalDateTime.now());
        return user;
    }

    @Nested
    @DisplayName("게시글 등록")
    class Create {

        private final CommunityPostCreateRequest request = new CommunityPostCreateRequest(
                "제목", "내용", BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        @Test
        @DisplayName("정상 유저면 게시글을 저장하고 chatRoomId는 null로 응답한다")
        void createsPost() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(activeUser());

            CommunityPostCreateResponse response = service.create(USER_ID, request);

            ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
            verify(communityPostRepository).save(captor.capture());
            CommunityPost saved = captor.getValue();
            assertThat(saved.getTitle()).isEqualTo("제목");
            assertThat(saved.getContent()).isEqualTo("내용");
            assertThat(saved.getLat()).isEqualByComparingTo(BigDecimal.valueOf(37.5));
            assertThat(saved.getLng()).isEqualByComparingTo(BigDecimal.valueOf(127.0));
            // TODO: Chat 도메인 개발 전까지 chatRoomId는 null이어야 한다.
            assertThat(response.chatRoomId()).isNull();
        }

        @Test
        @DisplayName("탈퇴한 유저면 401을 던지고 저장하지 않는다")
        void rejectsWithdrawnUser() {
            given(entityManager.getReference(User.class, USER_ID)).willReturn(withdrawnUser());

            assertThatThrownBy(() -> service.create(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.UNAUTHORIZED);

            verify(communityPostRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("게시글 상세 조회")
    class Find {

        @Test
        @DisplayName("존재하지 않는 게시글이면 404를 던진다")
        void throwsNotFoundWhenMissing() {
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.find(POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommunityErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("삭제된 게시글이면 410을 던진다")
        void throwsGoneWhenDeleted() {
            CommunityPost deletedPost = mock(CommunityPost.class);
            given(deletedPost.isDeleted()).willReturn(true);
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(deletedPost));

            assertThatThrownBy(() -> service.find(POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommunityErrorCode.COMMUNITY_POST_DELETED);
        }

        @Test
        @DisplayName("정상 게시글이면 상세 DTO를 반환한다")
        void returnsDetail() {
            CommunityPost post = mock(CommunityPost.class);
            LocalDateTime createdAt = LocalDateTime.now();
            given(post.isDeleted()).willReturn(false);
            given(post.getId()).willReturn(POST_ID);
            given(post.getAuthor()).willReturn(activeUser());
            given(post.getTitle()).willReturn("제목");
            given(post.getContent()).willReturn("내용");
            given(post.getLat()).willReturn(BigDecimal.valueOf(37.5));
            given(post.getLng()).willReturn(BigDecimal.valueOf(127.0));
            given(post.getCommentCount()).willReturn(3);
            given(post.getCreatedAt()).willReturn(createdAt);
            given(communityPostRepository.findById(POST_ID)).willReturn(Optional.of(post));

            CommunityPostDetailResponse response = service.find(POST_ID);

            assertThat(response.id()).isEqualTo(POST_ID);
            assertThat(response.title()).isEqualTo("제목");
            assertThat(response.content()).isEqualTo("내용");
            assertThat(response.commentCount()).isEqualTo(3);
            assertThat(response.createdAt()).isEqualTo(createdAt);
        }
    }
}
