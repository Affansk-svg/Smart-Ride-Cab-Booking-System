package com.smartride.user.security;

import com.smartride.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generate(User u) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("email", u.getEmail())
                .claim("name", u.getName())
                .claim("role", u.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public AuthUser toUser(Claims c) {
        return new AuthUser(Long.valueOf(c.getSubject()), c.get("email", String.class),
                c.get("name", String.class), c.get("role", String.class));
    }
}
