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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_recipient_type_event",
                columnNames = {"recipient_id", "type", "event_id"}),
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    public static Notification createSettlementReminder(User recipient, Long settlementId, ChatRoom targetChatRoom,
                                                        String content, LocalDateTime now) {
        Notification notification = createForChatRoom(recipient, NotificationType.SETTLEMENT_COMPLETION_REMINDER,
                settlementId, targetChatRoom, content);
        notification.createdAt = now;
        return notification;
    }

    public void remind(String content, LocalDateTime now) {
        if (type != NotificationType.SETTLEMENT_COMPLETION_REMINDER) {
            throw new IllegalStateException("정산 완료 리마인드 알림만 다시 보낼 수 있습니다. type=" + type);
        }
        this.content = content;
        this.createdAt = now;
        this.readAt = null;
    }

    @PrePersist
    private void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
