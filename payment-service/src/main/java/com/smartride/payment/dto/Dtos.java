package com.smartride.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartride.payment.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public final class Dtos {
    private Dtos() {}

    public record PaymentRequest(@NotNull Long rideId, @NotNull PaymentMethod method) {}

    /** Subset of the ride-service response that payment-service cares about. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RideView(Long id, Long riderId, String status, Double fare) {}
}
