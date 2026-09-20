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

    @Query("SELECT p FROM CommunityPost p WHERE p.id = :id")
    Optional<CommunityPost> findActiveById(@Param("id") Long id);


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


    @Modifying
    @Query("UPDATE CommunityPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    int increaseCommentCount(@Param("postId") Long postId);
}
