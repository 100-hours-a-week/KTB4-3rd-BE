package com.ktb.moyeota.domain.companion.entity;

import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "companions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Companion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 20)
    private TransportType transportType;

    @Column(length = 500)
    private String content;

    @Column(name = "origin_name", nullable = false, length = 100)
    private String originName;

    @Column(name = "origin_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal originLat;

    @Column(name = "origin_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal originLng;

    @Column(name = "dest_name", nullable = false, length = 100)
    private String destName;

    @Column(name = "dest_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal destLat;

    @Column(name = "dest_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal destLng;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "eta_at")
    private LocalDateTime etaAt;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Companion(User creator, User host, CompanionKind kind, TransportType transportType,
                       String content, String originName, BigDecimal originLat, BigDecimal originLng,
                       String destName, BigDecimal destLat, BigDecimal destLng,
                       LocalDateTime departureAt, Integer capacity) {
        this.creator = creator;
        this.host = host;
        this.kind = kind;
        this.transportType = transportType; // 매칭팟에는 필요없음
        this.content = content; // 매칭팟에는 필요없음
        this.originName = originName;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destName = destName;
        this.destLat = destLat;
        this.destLng = destLng;
        this.departureAt = departureAt;
        this.capacity = capacity; // 매칭팟은 4명으로 고정
        this.currentCount = 1;
        this.status = CompanionStatus.RECRUITING;
    }

    public static Companion createRecruiting(User host, TransportType transportType, String content,
                                              String originName, BigDecimal originLat, BigDecimal originLng,
                                              String destName, BigDecimal destLat, BigDecimal destLng,
                                              LocalDateTime departureAt, Integer capacity) {
        return new Companion(host, host, CompanionKind.COMPANION, transportType, content,
                originName, originLat, originLng, destName, destLat, destLng, departureAt, capacity);
    }

    public void transferHost(User newHost) {
        this.host = newHost;
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
