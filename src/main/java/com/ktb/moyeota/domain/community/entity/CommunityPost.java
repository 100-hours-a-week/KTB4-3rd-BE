package com.ktb.moyeota.domain.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    // location은 lat/lng로부터 DB가 계산하는 생성 컬럼(STORED)이라 엔티티에 매핑하지 않는다.
    // 지도 핀 SPATIAL INDEX 조회는 Repository의 native query에서 컬럼명을 직접 참조한다.

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private CommunityPost(Long authorId, String title, String content, BigDecimal lat, BigDecimal lng) {
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.lat = lat;
        this.lng = lng;
        this.commentCount = 0;
    }

    public static CommunityPost create(Long authorId, String title, String content, BigDecimal lat, BigDecimal lng) {
        return new CommunityPost(authorId, title, content, lat, lng);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
