package com.smartride.payment.web;

import com.smartride.payment.dto.Dtos.PaymentRequest;
import com.smartride.payment.model.Payment;
import com.smartride.payment.security.AuthUser;
import com.smartride.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService payments;

    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<Payment> pay(@AuthenticationPrincipal AuthUser u, Authentication auth,
                                       @Valid @RequestBody PaymentRequest req) {
        return ResponseEntity.status(201).body(payments.pay(u, (String) auth.getCredentials(), req));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('RIDER')")
    public List<Payment> mine(@AuthenticationPrincipal AuthUser u) { return payments.mine(u); }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Payment> all() { return payments.all(); }
}
