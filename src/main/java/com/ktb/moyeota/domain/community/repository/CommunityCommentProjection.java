package com.ktb.moyeota.domain.community.repository;

import java.time.LocalDateTime;

/** 댓글 목록 조회용 projection — community_comments 컬럼만 담는다. */
public interface CommunityCommentProjection {

    Long getId();

    Long getAuthorId();

    String getContent();

    LocalDateTime getCreatedAt();

    /**
     * [주의할 내용] User 엔티티가 아직 없어 작성자 닉네임을 조회하지 못한다.
     * native query로 users 테이블을 직접 참조하지 않기로 했으므로 우선 null을 반환한다.
     * User 도메인 개발 후 실제 닉네임 조회 로직으로 교체해야 한다.
     */
    default String getAuthorNickname() {
        return null;
    }
}
