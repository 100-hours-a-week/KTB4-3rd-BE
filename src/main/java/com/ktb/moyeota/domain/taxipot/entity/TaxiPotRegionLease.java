package com.ktb.moyeota.domain.taxipot.entity;

import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "taxi_pot_region_leases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_region_leases_region", columnNames = {"region_row", "region_col"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxiPotRegionLease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_row", nullable = false)
    private Long regionRow;

    @Column(name = "region_col", nullable = false)
    private Long regionCol;

    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "lease_until", nullable = false)
    private LocalDateTime leaseUntil;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private TaxiPotRegionLease(RegionCell region, String ownerId, LocalDateTime leaseUntil, LocalDateTime now) {
        this.regionRow = region.row();
        this.regionCol = region.col();
        this.ownerId = ownerId;
        this.leaseUntil = leaseUntil;
        this.updatedAt = now;
    }

    public static TaxiPotRegionLease claim(
            RegionCell region, String ownerId, LocalDateTime leaseUntil, LocalDateTime now) {
        return new TaxiPotRegionLease(region, ownerId, leaseUntil, now);
    }
}
