package com.smartride.payment.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        var fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "Validation failed" : fe.getField() + ": " + fe.getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> denied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict(Exception e) {
        return ResponseEntity.status(409).body(Map.of("error", "Conflict: the resource was modified by someone else"));
    }
}
