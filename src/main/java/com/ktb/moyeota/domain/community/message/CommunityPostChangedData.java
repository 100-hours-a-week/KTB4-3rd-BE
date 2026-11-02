package com.ktb.moyeota.domain.community.message;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import com.ktb.moyeota.domain.community.entity.CommunityPost;

public record CommunityPostChangedData(
        CommunityPostData post,
        CommunityCommentData comment) {

    public static CommunityPostChangedData ofPost(CommunityPost post) {
        return new CommunityPostChangedData(CommunityPostData.from(post), null);
    }

    public static CommunityPostChangedData ofComment(CommunityComment comment) {
        return new CommunityPostChangedData(
                CommunityPostData.from(comment.getPost()), CommunityCommentData.from(comment));
    }
}
