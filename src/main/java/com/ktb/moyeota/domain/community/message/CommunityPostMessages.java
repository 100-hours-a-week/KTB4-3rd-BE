package com.ktb.moyeota.domain.community.message;

public final class CommunityPostMessages {

    public static final String CHANGED_TOPIC = "community.post.changed";
    public static final int SCHEMA_VERSION = 1;

    public static final String POST_CREATED = "community.post.created";
    public static final String COMMENT_CREATED = "community.comment.created";

    private CommunityPostMessages() {
    }

    public static String key(Long postId) {
        return String.valueOf(postId);
    }
}
