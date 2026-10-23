package com.smartride.payment.service;

import com.smartride.payment.dto.Dtos.*;
import com.smartride.payment.exception.ApiException;
import com.smartride.payment.model.Payment;
import com.smartride.payment.model.PaymentStatus;
import com.smartride.payment.repo.PaymentRepository;
import com.smartride.payment.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository repo;
    private final RestTemplate rest;

    @Value("${app.ride-service-url}")
    private String rideServiceUrl;

    /**
     * Inter-service call: asks ride-service (forwarding the rider's JWT) for the ride,
     * verifies ownership + completion, then records a simulated successful payment.
     */
    public Payment pay(AuthUser u, String token, PaymentRequest req) {
        RideView ride = fetchRide(req.rideId(), token);
        if (ride == null) throw new ApiException(404, "Ride not found");
        if (!ride.riderId().equals(u.id())) throw new ApiException(403, "Not your ride");
        if (!"COMPLETED".equals(ride.status())) throw new ApiException(409, "Ride is not completed yet");
        if (repo.existsByRideId(ride.id())) throw new ApiException(409, "Ride already paid");

        Payment p = new Payment();
        p.setRideId(ride.id());
        p.setRiderId(u.id());
        p.setAmount(ride.fare());
        p.setMethod(req.method());
        p.setStatus(PaymentStatus.SUCCESS); // a real gateway (Razorpay/Stripe) would be called here
        p.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return repo.save(p);
    }

    public List<Payment> mine(AuthUser u) { return repo.findByRiderIdOrderByCreatedAtDesc(u.id()); }

    public List<Payment> all() { return repo.findAll(); }

    private RideView fetchRide(Long id, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        try {
            return rest.exchange(rideServiceUrl + "/api/rides/" + id, HttpMethod.GET,
                    new HttpEntity<>(h), RideView.class).getBody();
        } catch (HttpClientErrorException e) {
            throw new ApiException(e.getStatusCode().value(), "Ride lookup failed");
        } catch (RestClientException e) {
            throw new ApiException(503, "Ride service unavailable");
        }
    }
}
