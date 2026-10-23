package com.smartride.ride.dto;

import jakarta.validation.constraints.*;

public final class Dtos {
    private Dtos() {}

    public record RideRequest(
            String pickupAddress,
            String dropAddress,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double dropLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double dropLng) {}

    public record LocationUpdate(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {}
}
