package com.smartride.ride.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "rides")
@Getter @Setter @NoArgsConstructor
public class Ride {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic locking: prevents two drivers from accepting the same ride. */
    @Version
    private Long version;

    @Column(nullable = false)
    private Long riderId;
    private String riderName;

    private Long driverId;
    private String driverName;

    private String pickupAddress;
    private String dropAddress;
    private double pickupLat;
    private double pickupLng;
    private double dropLat;
    private double dropLng;

    private double distanceKm;
    private double fare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
