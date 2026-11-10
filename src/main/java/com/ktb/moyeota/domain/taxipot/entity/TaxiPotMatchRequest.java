package com.ktb.moyeota.domain.taxipot.entity;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "taxi_pot_match_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_requests_matching_user", columnNames = "matching_user_id"),
        indexes = {
                @Index(name = "idx_match_requests_status_region",
                        columnList = "status, region_row, region_col, departure_at"),
                @Index(name = "idx_match_requests_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxiPotMatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    @Column(name = "region_row", nullable = false)
    private Long regionRow;

    @Column(name = "region_col", nullable = false)
    private Long regionCol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxiPotMatchRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "companion_id")
    private Companion companion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "matching_user_id", insertable = false, updatable = false,
            columnDefinition = "BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'MATCHING' THEN user_id END)")
    private Long matchingUserId;

    private TaxiPotMatchRequest(User user, String originName, BigDecimal originLat, BigDecimal originLng,
                                String destName, BigDecimal destLat, BigDecimal destLng,
                                LocalDateTime departureAt, RegionCell region) {
        this.user = user;
        this.originName = originName;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destName = destName;
        this.destLat = destLat;
        this.destLng = destLng;
        this.departureAt = departureAt;
        this.regionRow = region.row();
        this.regionCol = region.col();
        this.status = TaxiPotMatchRequestStatus.MATCHING;
    }

    public static TaxiPotMatchRequest start(User user, String originName, BigDecimal originLat, BigDecimal originLng,
                                            String destName, BigDecimal destLat, BigDecimal destLng,
                                            LocalDateTime departureAt, RegionCell region) {
        return new TaxiPotMatchRequest(user, originName, originLat, originLng,
                destName, destLat, destLng, departureAt, region);
    }

    public boolean isMatching() {
        return status == TaxiPotMatchRequestStatus.MATCHING;
    }

    public boolean isMatchedTo(Long companionId) {
        return status == TaxiPotMatchRequestStatus.MATCHED && companion != null
                && companion.getId().equals(companionId);
    }

    public void matchTo(Companion companion) {
        if (!isMatching()) {
            throw new IllegalStateException("찾는 중이 아닌 요청은 매칭할 수 없다: " + id);
        }
        this.status = TaxiPotMatchRequestStatus.MATCHED;
        this.companion = companion;
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
