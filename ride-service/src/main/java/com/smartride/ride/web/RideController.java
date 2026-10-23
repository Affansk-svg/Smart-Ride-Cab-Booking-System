package com.smartride.ride.web;

import com.smartride.ride.dto.Dtos.*;
import com.smartride.ride.model.Ride;
import com.smartride.ride.security.AuthUser;
import com.smartride.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {
    private final RideService rides;

    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<Ride> request(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody RideRequest r) {
        return ResponseEntity.status(201).body(rides.request(u, r));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('RIDER','DRIVER')")
    public List<Ride> mine(@AuthenticationPrincipal AuthUser u) { return rides.mine(u); }

    @GetMapping("/available")
    @PreAuthorize("hasRole('DRIVER')")
    public List<Ride> available() { return rides.available(); }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Ride> all() { return rides.all(); }

    @GetMapping("/{id}")
    public Ride get(@PathVariable Long id, @AuthenticationPrincipal AuthUser u) { return rides.get(id, u); }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public Ride accept(@PathVariable Long id, @AuthenticationPrincipal AuthUser u) { return rides.accept(id, u); }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public Ride start(@PathVariable Long id, @AuthenticationPrincipal AuthUser u) { return rides.start(id, u); }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public Ride complete(@PathVariable Long id, @AuthenticationPrincipal AuthUser u) { return rides.complete(id, u); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RIDER','DRIVER')")
    public Ride cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser u) { return rides.cancel(id, u); }

    @PostMapping("/{id}/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> location(@PathVariable Long id, @AuthenticationPrincipal AuthUser u,
                                         @Valid @RequestBody LocationUpdate loc) {
        rides.location(id, u, loc);
        return ResponseEntity.noContent().build();
    }
}
