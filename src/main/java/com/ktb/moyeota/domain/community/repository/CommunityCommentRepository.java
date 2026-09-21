package com.ktb.moyeota.domain.community.repository;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    @Query("""
            SELECT c
            FROM CommunityComment c
            JOIN FETCH c.author
            WHERE c.post.id = :postId
              AND (:cursor IS NULL OR c.id < :cursor)
            ORDER BY c.id DESC
            """)
    List<CommunityComment> findByPostIdWithAuthor(
            @Param("postId") Long postId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
