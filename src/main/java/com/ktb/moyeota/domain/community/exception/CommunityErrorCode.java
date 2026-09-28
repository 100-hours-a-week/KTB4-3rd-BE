package com.ktb.moyeota.domain.community.exception;

import com.ktb.moyeota.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {

    COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."),

    COMMUNITY_POST_DELETED(HttpStatus.GONE, "삭제된 게시글입니다."),

    COMMUNITY_COMMENT_POST_DELETED(HttpStatus.CONFLICT, "삭제된 게시글에는 댓글을 작성할 수 없습니다."),

    COMMUNITY_VIEWPORT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "뷰포트 좌표 범위가 올바르지 않습니다."),

    COMMUNITY_VIEWPORT_TOO_LARGE(HttpStatus.BAD_REQUEST, "조회 범위가 너무 넓습니다. 지도를 확대해주세요.");

    private final HttpStatus status;
    private final String message;
}
