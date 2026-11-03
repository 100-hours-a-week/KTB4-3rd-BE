package com.ktb.moyeota.domain.congestion.message;

import com.ktb.moyeota.global.event.InvalidEventException;

public record PostChangedData(ChangedPost post, ChangedComment comment) {

    public PostChangedData {
        if (post == null || post.postId() == null || post.lat() == null || post.lng() == null
                || post.createdAt() == null) {
            throw new InvalidEventException("글 바뀜 메시지에 원글 post_id · 좌표 · 작성 시각이 있어야 합니다");
        }
        if (comment != null && (comment.commentId() == null || comment.createdAt() == null)) {
            throw new InvalidEventException(
                    "글 바뀜 메시지의 댓글에 comment_id · 작성 시각이 있어야 합니다. post_id=" + post.postId());
        }
    }

    public boolean isComment() {
        return comment != null;
    }
}
