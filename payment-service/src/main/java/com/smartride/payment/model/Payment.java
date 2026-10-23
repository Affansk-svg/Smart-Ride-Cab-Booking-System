package com.smartride.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(columnNames = "rideId"))
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long rideId;

    @Column(nullable = false)
    private Long riderId;

    private double amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String transactionRef;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
