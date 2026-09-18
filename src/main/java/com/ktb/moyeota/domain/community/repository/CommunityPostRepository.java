package com.ktb.moyeota.domain.community.repository;

import com.ktb.moyeota.domain.community.entity.CommunityPost;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {


    /**
     * 게시글 상세조회(및 댓글 목록조회)에서 재사용하는 조회 메서드.
     * [참고] 이름은 findActiveById지만 deleted_at 필터링은 하지 않는다 — 존재하지 않는 경우(404)와
     * soft-delete된 경우(410)를 Service 계층에서 구분해야 해서, row는 deleted_at 상태와 무관하게 그대로 반환하고
     * Service에서 post.isDeleted()로 410 여부를 판단하는 구조로 잡았다. 이름과 동작이 안 맞아 보이면 알려주세요.
     */
    @Query("SELECT p FROM CommunityPost p WHERE p.id = :id")
    Optional<CommunityPost> findActiveById(@Param("id") Long id);

    /**
     * 지도 핀(map-pins) 조회 — community_posts.location SPATIAL INDEX(MBRContains)로 뷰포트 안 후보를 좁히고
     * deleted_at IS NULL로 필터.
     */
    @Query(nativeQuery = true, value = """
            SELECT cp.id AS id, cp.title AS title, cp.lat AS lat, cp.lng AS lng
            FROM community_posts cp
            WHERE cp.deleted_at IS NULL
              AND MBRContains(
                    ST_GeomFromText(CONCAT('POLYGON((', :swLat, ' ', :swLng, ',', :neLat, ' ', :swLng, ',', :neLat, ' ', :neLng, ',', :swLat, ' ', :neLng, ',', :swLat, ' ', :swLng, '))'), 4326),
                    cp.location
                  )
            """)
    List<CommunityPinProjection> findPinsInViewport(
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng);

    /**
     * 댓글 작성 시 comment_count를 원자적으로 +1. 애플리케이션에서 읽어서 +1하지 않고
     * DB의 UPDATE ... SET comment_count = comment_count + 1로 처리해 레이스 컨디션을 피한다.
     */
    @Modifying
    @Query("UPDATE CommunityPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    int increaseCommentCount(@Param("postId") Long postId);
}
