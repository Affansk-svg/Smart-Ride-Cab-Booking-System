package com.smartride.payment.security;

public record AuthUser(Long id, String email, String name, String role) {}
