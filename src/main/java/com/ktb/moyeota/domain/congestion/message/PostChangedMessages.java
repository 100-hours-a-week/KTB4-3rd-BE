package com.ktb.moyeota.domain.congestion.message;

public final class PostChangedMessages {

    public static final String TOPIC = "community.post.changed";
    public static final int SCHEMA_VERSION = 1;

    public static final String POST_CREATED = "community.post.created";
    public static final String COMMENT_CREATED = "community.comment.created";

    private PostChangedMessages() {
    }
}
