package com.smartride.user.dto;

import com.smartride.user.model.Role;
import jakarta.validation.constraints.*;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "password must be at least 6 characters") String password,
            String phone,
            @NotNull Role role) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, Long id, String name, String email, Role role) {}

    public record UserView(Long id, String name, String email, String phone, Role role) {}
}
