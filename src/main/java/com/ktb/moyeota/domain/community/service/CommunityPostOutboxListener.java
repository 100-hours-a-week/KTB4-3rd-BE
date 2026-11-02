package com.ktb.moyeota.domain.community.service;

import com.ktb.moyeota.domain.community.entity.CommunityComment;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.community.event.CommunityCommentCreatedEvent;
import com.ktb.moyeota.domain.community.event.CommunityPostCreatedEvent;
import com.ktb.moyeota.domain.community.message.CommunityPostChangedData;
import com.ktb.moyeota.domain.community.message.CommunityPostMessages;
import com.ktb.moyeota.domain.community.repository.CommunityCommentRepository;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityPostOutboxListener {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onPostCreated(CommunityPostCreatedEvent event) {
        CommunityPost post = communityPostRepository.findById(event.postId())
                .orElseThrow(() -> new IllegalStateException("게시글이 없다: " + event.postId()));
        record(post.getId(), CommunityPostMessages.POST_CREATED, CommunityPostChangedData.ofPost(post));
    }

    @EventListener
    public void onCommentCreated(CommunityCommentCreatedEvent event) {
        CommunityComment comment = communityCommentRepository.findById(event.commentId())
                .orElseThrow(() -> new IllegalStateException("댓글이 없다: " + event.commentId()));
        record(comment.getPost().getId(), CommunityPostMessages.COMMENT_CREATED,
                CommunityPostChangedData.ofComment(comment));
    }

    private void record(Long postId, String eventType, CommunityPostChangedData data) {
        outboxRecorder.record(CommunityPostMessages.CHANGED_TOPIC, CommunityPostMessages.key(postId), eventType,
                CommunityPostMessages.SCHEMA_VERSION, data);
    }
}
