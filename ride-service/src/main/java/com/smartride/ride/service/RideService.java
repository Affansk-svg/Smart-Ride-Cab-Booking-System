package com.smartride.ride.service;

import com.smartride.ride.dto.Dtos.*;
import com.smartride.ride.exception.ApiException;
import com.smartride.ride.model.Ride;
import com.smartride.ride.model.RideStatus;
import com.smartride.ride.repo.RideRepository;
import com.smartride.ride.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RideService {
    static final double BASE_FARE = 40.0;
    static final double PER_KM = 12.0;

    private final RideRepository repo;
    private final SimpMessagingTemplate ws;

    @Transactional
    public Ride request(AuthUser u, RideRequest r) {
        double km = GeoUtil.km(r.pickupLat(), r.pickupLng(), r.dropLat(), r.dropLng());
        if (km < 0.1) throw new ApiException(400, "Pickup and drop are too close");
        Ride ride = new Ride();
        ride.setRiderId(u.id());
        ride.setRiderName(u.name());
        ride.setPickupAddress(r.pickupAddress());
        ride.setDropAddress(r.dropAddress());
        ride.setPickupLat(r.pickupLat());
        ride.setPickupLng(r.pickupLng());
        ride.setDropLat(r.dropLat());
        ride.setDropLng(r.dropLng());
        ride.setDistanceKm(Math.round(km * 100) / 100.0);
        ride.setFare(Math.round((BASE_FARE + PER_KM * km) * 100) / 100.0);
        ride.setStatus(RideStatus.REQUESTED);
        return repo.save(ride);
    }

    @Transactional(readOnly = true)
    public List<Ride> mine(AuthUser u) {
        return "DRIVER".equals(u.role())
                ? repo.findByDriverIdOrderByCreatedAtDesc(u.id())
                : repo.findByRiderIdOrderByCreatedAtDesc(u.id());
    }

    @Transactional(readOnly = true)
    public List<Ride> available() { return repo.findByStatusOrderByCreatedAtDesc(RideStatus.REQUESTED); }

    @Transactional(readOnly = true)
    public List<Ride> all() { return repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt")); }

    @Transactional(readOnly = true)
    public Ride get(Long id, AuthUser u) {
        Ride r = find(id);
        boolean allowed = "ADMIN".equals(u.role()) || r.getRiderId().equals(u.id())
                || (r.getDriverId() != null && r.getDriverId().equals(u.id()));
        if (!allowed) throw new ApiException(403, "Not your ride");
        return r;
    }

    @Transactional
    public Ride accept(Long id, AuthUser driver) {
        Ride r = find(id);
        if (r.getStatus() != RideStatus.REQUESTED) throw new ApiException(409, "Ride is no longer available");
        r.setDriverId(driver.id());
        r.setDriverName(driver.name());
        return transition(r, RideStatus.ACCEPTED);
    }

    @Transactional
    public Ride start(Long id, AuthUser driver) {
        Ride r = requireDriver(find(id), driver);
        expect(r, RideStatus.ACCEPTED);
        return transition(r, RideStatus.STARTED);
    }

    @Transactional
    public Ride complete(Long id, AuthUser driver) {
        Ride r = requireDriver(find(id), driver);
        expect(r, RideStatus.STARTED);
        return transition(r, RideStatus.COMPLETED);
    }

    @Transactional
    public Ride cancel(Long id, AuthUser u) {
        Ride r = find(id);
        boolean isRider = r.getRiderId().equals(u.id());
        boolean isDriver = r.getDriverId() != null && r.getDriverId().equals(u.id());
        if (!isRider && !isDriver) throw new ApiException(403, "Not your ride");
        if (r.getStatus() != RideStatus.REQUESTED && r.getStatus() != RideStatus.ACCEPTED)
            throw new ApiException(409, "Ride can no longer be cancelled");
        return transition(r, RideStatus.CANCELLED);
    }

    /** Driver pushes a GPS position; it is relayed to subscribers of /topic/rides/{id}/location. */
    @Transactional(readOnly = true)
    public void location(Long id, AuthUser driver, LocationUpdate loc) {
        Ride r = requireDriver(find(id), driver);
        if (r.getStatus() != RideStatus.ACCEPTED && r.getStatus() != RideStatus.STARTED)
            throw new ApiException(409, "Ride is not active");
        ws.convertAndSend("/topic/rides/" + id + "/location",
                Map.of("lat", loc.lat(), "lng", loc.lng(), "ts", System.currentTimeMillis()));
    }

    private Ride transition(Ride r, RideStatus next) {
        r.setStatus(next);
        Ride saved = repo.saveAndFlush(r);
        ws.convertAndSend("/topic/rides/" + saved.getId() + "/status", saved);
        return saved;
    }

    private Ride find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(404, "Ride not found"));
    }

    private Ride requireDriver(Ride r, AuthUser u) {
        if (r.getDriverId() == null || !r.getDriverId().equals(u.id()))
            throw new ApiException(403, "You are not the driver of this ride");
        return r;
    }

    private void expect(Ride r, RideStatus s) {
        if (r.getStatus() != s) throw new ApiException(409, "Ride must be " + s + " but is " + r.getStatus());
    }
}
