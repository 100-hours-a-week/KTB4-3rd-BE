package com.ktb.moyeota.domain.companion.entity;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import jakarta.persistence.*;
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

    private static final int MIN_CAPACITY = 1;
    private static final int CAR_MAX_CAPACITY = 4;
    private static final int PUBLIC_TRANSPORT_MAX_CAPACITY = 10;
    private static final int MIN_RIDE_PARTICIPANTS = 2;
    private static final int TAXI_POT_CAPACITY = 4;

    private Companion(User creator, User host, CompanionKind kind, TransportType transportType,
                       String content, String originName, BigDecimal originLat, BigDecimal originLng,
                       String destName, BigDecimal destLat, BigDecimal destLng,
                       LocalDateTime departureAt, Integer capacity) {
        this.creator = creator;
        this.host = host;
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
        this.currentCount = 1;
        this.status = CompanionStatus.RECRUITING;
    }

    public static Companion createCompanionPost(User host, CompanionKind kind, TransportType transportType,
                                                  String content,
                                                  String originName, BigDecimal originLat, BigDecimal originLng,
                                                  String destName, BigDecimal destLat, BigDecimal destLng,
                                                  LocalDateTime departureAt, Integer capacity) {
        return new Companion(host, host, kind, transportType, content,
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

    public static Companion openTaxiPot(User host, String originName, BigDecimal originLat, BigDecimal originLng,
                                        String destName, BigDecimal destLat, BigDecimal destLng,
                                        LocalDateTime departureAt) {
        return new Companion(host, host, CompanionKind.TAXI_POT, TransportType.TAXI, null,
                originName, originLat, originLng, destName, destLat, destLng, departureAt, TAXI_POT_CAPACITY);
    }

    public boolean isDepartureAtFuture() {
        return departureAt != null && departureAt.isAfter(LocalDateTime.now());
    }

    public boolean isOriginDestDifferent() {
        return !(originLat.compareTo(destLat) == 0 && originLng.compareTo(destLng) == 0);
    }

    public boolean isCapacityValid() {
        int max = maxCapacityFor(transportType);
        return capacity != null && capacity >= MIN_CAPACITY && capacity <= max;
    }

    private static int maxCapacityFor(TransportType transportType) {
        return switch (transportType) {
            case TAXI, OWNED_CAR -> CAR_MAX_CAPACITY;
            case SUBWAY, BUS -> PUBLIC_TRANSPORT_MAX_CAPACITY;
        };
    }

    public CompanionParticipant join(User user, CompanionParticipant previous) {
        if (status != CompanionStatus.RECRUITING || currentCount >= capacity) {
            throw new IllegalStateException("모집 중인 자리가 없는 동행에 합류할 수 없다: " + id);
        }
        this.currentCount++;
        return CompanionParticipant.join(this, user, previous);
    }

    public void startRide(LocalDateTime now) {
        if (status != CompanionStatus.RECRUITING) {
            throw new BusinessException(CompanionErrorCode.INVALID_STATE_TRANSITION);
        }
        if (currentCount < MIN_RIDE_PARTICIPANTS) {
            throw new BusinessException(CompanionErrorCode.NOT_ENOUGH_PARTICIPANTS);
        }
        if (departureAt.isAfter(now)) {
            throw new BusinessException(CompanionErrorCode.DEPARTURE_NOT_REACHED);
        }
        this.status = CompanionStatus.IN_PROGRESS;
    }

    public void completeRide() {
        if (status != CompanionStatus.IN_PROGRESS) {
            throw new BusinessException(CompanionErrorCode.INVALID_STATE_TRANSITION);
        }
        this.status = CompanionStatus.COMPLETED;
    }

    public void transferHost(User newHost) {
        this.host = newHost;
    }
}
