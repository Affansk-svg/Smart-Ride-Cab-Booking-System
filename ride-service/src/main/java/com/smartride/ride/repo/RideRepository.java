package com.smartride.ride.repo;

import com.smartride.ride.model.Ride;
import com.smartride.ride.model.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByRiderIdOrderByCreatedAtDesc(Long riderId);
    List<Ride> findByDriverIdOrderByCreatedAtDesc(Long driverId);
    List<Ride> findByStatusOrderByCreatedAtDesc(RideStatus status);
}
