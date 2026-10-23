package com.smartride.user.security;

public record AuthUser(Long id, String email, String name, String role) {}
