package com.smartride.ride.security;

public record AuthUser(Long id, String email, String name, String role) {}
