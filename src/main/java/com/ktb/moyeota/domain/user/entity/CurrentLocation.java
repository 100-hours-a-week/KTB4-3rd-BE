package com.ktb.moyeota.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "current_locations",
        uniqueConstraints = @UniqueConstraint(name = "uk_current_locations_user", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurrentLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_lat", precision = 9, scale = 6)
    private BigDecimal currentLat;

    @Column(name = "current_lng", precision = 9, scale = 6)
    private BigDecimal currentLng;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private CurrentLocation(User user, BigDecimal currentLat, BigDecimal currentLng) {
        this.user = user;
        this.currentLat = currentLat;
        this.currentLng = currentLng;
    }

    public static CurrentLocation of(User user, BigDecimal lat, BigDecimal lng) {
        return new CurrentLocation(user, lat, lng);
    }

    public void move(BigDecimal lat, BigDecimal lng) {
        this.currentLat = lat;
        this.currentLng = lng;
    }
}
