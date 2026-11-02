package com.ktb.moyeota.domain.congestion.document;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signal {

    @Id
    private String id;

    @Field("post_id")
    private Long postId;

    @Field("comment_id")
    private Long commentId;

    @Field("spot_id")
    private Long spotId;

    @Field("author_key")
    private String authorKey;

    @Field("created_at")
    private Instant createdAt;

    private String title;

    private String content;

    private Signal(Long postId, Long commentId, Long spotId, String authorKey, Instant createdAt,
            String title, String content) {
        this.postId = postId;
        this.commentId = commentId;
        this.spotId = spotId;
        this.authorKey = authorKey;
        this.createdAt = createdAt;
        this.title = title;
        this.content = content;
    }

    public static Signal ofPost(Long postId, Long spotId, String authorKey, Instant createdAt,
            String title, String content) {
        return new Signal(postId, null, spotId, authorKey, createdAt, title, content);
    }

    public static Signal ofComment(Long postId, Long commentId, Long spotId, String authorKey,
            Instant createdAt, String content) {
        return new Signal(postId, commentId, spotId, authorKey, createdAt, null, content);
    }

    public boolean isComment() {
        return commentId != null;
    }
}
