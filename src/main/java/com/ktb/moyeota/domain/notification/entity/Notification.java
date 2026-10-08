package com.ktb.moyeota.domain.notification.entity;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_recipient_created", columnList = "recipient_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_chat_room_id")
    private ChatRoom targetChatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_post_id")
    private CommunityPost targetPost;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    private Notification(User recipient, NotificationType type, Long eventId,
                         ChatRoom targetChatRoom, CommunityPost targetPost, String content) {
        this.recipient = recipient;
        this.type = type;
        this.eventId = eventId;
        this.targetChatRoom = targetChatRoom;
        this.targetPost = targetPost;
        this.content = content;
    }

    public static Notification createForChatRoom(User recipient, NotificationType type, Long eventId,
                                                 ChatRoom targetChatRoom, String content) {
        if (type.targetsPost()) {
            throw new IllegalArgumentException(type + " 알림은 게시글로 이동해야 합니다");
        }
        if (targetChatRoom == null) {
            throw new IllegalArgumentException("채팅방 이동 알림에는 targetChatRoom이 필요합니다");
        }
        return new Notification(recipient, type, eventId, targetChatRoom, null, content);
    }

    public static Notification createForComment(User recipient, Long commentId,
                                                CommunityPost targetPost, String content) {
        if (targetPost == null) {
            throw new IllegalArgumentException("댓글 알림에는 targetPost가 필요합니다");
        }
        return new Notification(recipient, NotificationType.COMMENT_CREATED, commentId, null, targetPost, content);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
