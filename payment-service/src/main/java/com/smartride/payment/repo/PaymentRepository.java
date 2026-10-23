package com.smartride.payment.repo;

import com.smartride.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByRiderIdOrderByCreatedAtDesc(Long riderId);
    boolean existsByRideId(Long rideId);
}
