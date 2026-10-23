package com.smartride.user.web;

import com.smartride.user.dto.Dtos.*;
import com.smartride.user.exception.ApiException;
import com.smartride.user.model.User;
import com.smartride.user.repo.UserRepository;
import com.smartride.user.security.AuthUser;
import com.smartride.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthService auth;
    private final UserRepository users;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
        return ResponseEntity.status(201).body(auth.register(r));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser principal) {
        User u = users.findById(principal.id()).orElseThrow(() -> new ApiException(404, "User not found"));
        return view(u);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> all() {
        return users.findAll().stream().map(UserController::view).toList();
    }

    private static UserView view(User u) {
        return new UserView(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole());
    }
}
