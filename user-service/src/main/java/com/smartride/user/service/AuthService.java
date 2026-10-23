package com.smartride.user.service;

import com.smartride.user.dto.Dtos.*;
import com.smartride.user.exception.ApiException;
import com.smartride.user.model.Role;
import com.smartride.user.model.User;
import com.smartride.user.repo.UserRepository;
import com.smartride.user.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthResponse register(RegisterRequest r) {
        if (r.role() == Role.ADMIN) throw new ApiException(403, "Cannot self-register as ADMIN");
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ApiException(409, "Email already registered");
        User u = new User();
        u.setName(r.name().trim());
        u.setEmail(email);
        u.setPhone(r.phone());
        u.setRole(r.role());
        u.setPassword(encoder.encode(r.password()));
        u = users.save(u);
        return toResponse(u);
    }

    public AuthResponse login(LoginRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(401, "Invalid email or password"));
        if (!encoder.matches(r.password(), u.getPassword()))
            throw new ApiException(401, "Invalid email or password");
        return toResponse(u);
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwt.generate(u), u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
