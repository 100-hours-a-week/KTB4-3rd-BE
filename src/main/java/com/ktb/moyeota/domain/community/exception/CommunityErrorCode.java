package com.ktb.moyeota.domain.community.exception;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Community 도메인(community_posts, community_comments) 전용 에러 코드.
 * 팀 컨벤션(PR #3) 기준 {DOMAIN}_{TARGET}_{STATE} 네이밍을 따른다.
 *
 * [주의할 내용] 같은 조건("게시글이 삭제됨")이 API별로 다른 상태 코드를 요구한다
 * (게시글 상세조회/댓글 목록조회 = 410, 댓글 작성 = 409 — 테크스펙에 "댓글 작성 409와
 * 상태 코드가 다른 점 주의"라고 명시돼 있음). 그래서 의미는 같지만 코드를 2개로 나눴다.
 * COMMUNITY_COMMENT_POST_DELETED라는 이름은 내가 임의로 정한 거라 마음에 안 들면 바꿔줘.
 */
@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {

    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."),

    COMMUNITY_POST_DELETED(HttpStatus.GONE, "삭제된 게시글입니다."),

    COMMUNITY_COMMENT_POST_DELETED(HttpStatus.CONFLICT, "삭제된 게시글에는 댓글을 작성할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
