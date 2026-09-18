package com.ktb.moyeota.domain.community.repository;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    // save(CommunityComment)는 JpaRepository가 기본 제공.

    /**
     * 댓글 목록 조회 — community_comments만 조회한다.
     * [주의할 내용] 작성자 닉네임(author_nickname)은 User 도메인 데이터라 아직 채울 수 없다.
     * User 엔티티/도메인이 없는 상태에서 users 테이블을 native query로 직접 참조하지 않기로 했고,
     * 그 부분(닉네임 조회)은 CommunityCommentProjection.getAuthorNickname()에 주석으로 남겨뒀다.
     * User 도메인 개발 후 이 쿼리(또는 Service 계층)에서 실제 닉네임을 채워야 한다.
     * [확인 필요] SQL(KTB-3rd-ERD.sql)에는 community_comments에 PK(id) 외 별도 인덱스가
     * 정의돼 있지 않다 — (post_id, id) 복합 인덱스를 추가할지는 DB 설계 사항이라 임의로
     * SQL을 바꾸지 않고 확인이 필요한 사항으로만 남긴다.
     * cursor가 null이면 최신 댓글부터, 있으면 그 값보다 작은 id부터 내림차순 조회한다
     * (개수 제한은 Pageable로 전달).
     */
    @Query("""
            SELECT c.id AS id, c.authorId AS authorId, c.content AS content, c.createdAt AS createdAt
            FROM CommunityComment c
            WHERE c.postId = :postId
              AND (:cursor IS NULL OR c.id < :cursor)
            ORDER BY c.id DESC
            """)
    List<CommunityCommentProjection> findByPostIdWithAuthor(
            @Param("postId") Long postId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
