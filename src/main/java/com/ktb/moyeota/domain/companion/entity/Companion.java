package com.ktb.moyeota.domain.companion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "companions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Companion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

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

    // origin_location은 origin_lat/origin_lng로부터 DB가 계산하는 생성 컬럼(STORED)이라
    // 엔티티에 매핑하지 않는다. SPATIAL INDEX 조회가 필요한 곳(지도 핀 등)은
    // Repository의 native query에서 컬럼명을 직접 참조한다.

    @Column(name = "dest_name", nullable = false, length = 100)
    private String destName;

    @Column(name = "dest_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal destLat;

    @Column(name = "dest_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal destLng;

    // dest_location도 동일한 이유로 매핑하지 않는다.

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

    private Companion(Long creatorId, Long hostId, CompanionKind kind, TransportType transportType,
                       String content, String originName, BigDecimal originLat, BigDecimal originLng,
                       String destName, BigDecimal destLat, BigDecimal destLng,
                       LocalDateTime departureAt, Integer capacity) {
        this.creatorId = creatorId;
        this.hostId = hostId;
        this.kind = kind;
        this.transportType = transportType;
        this.content = content;
        this.originName = originName;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destName = destName;
        this.destLat = destLat;
        this.destLng = destLng;
        this.departureAt = departureAt;
        this.capacity = capacity;
        // [확인 필요] 방장(host)을 등록 시점부터 현재 인원 1명으로 카운트하는 것으로 가정했다.
        // 처리 로직 문서에 currentCount 초기값이 명시돼 있지 않아 임의로 정한 값이니 팀 컨벤션 확인 필요.
        this.currentCount = 1;
        this.status = CompanionStatus.RECRUITING;
    }

    /**
     * 동행모집 게시글(POST /companion-posts) 등록. kind = COMPANION 고정,
     * creatorId = hostId = 등록한 사용자.
     */
    public static Companion createRecruiting(Long hostId, TransportType transportType, String content,
                                              String originName, BigDecimal originLat, BigDecimal originLng,
                                              String destName, BigDecimal destLat, BigDecimal destLng,
                                              LocalDateTime departureAt, Integer capacity) {
        return new Companion(hostId, hostId, CompanionKind.COMPANION, transportType, content,
                originName, originLat, originLng, destName, destLat, destLng, departureAt, capacity);
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
