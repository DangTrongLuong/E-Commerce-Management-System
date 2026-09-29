package com.example.ecommerce.common.util;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.common.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtUtil {

    @Value("${app.security.jwt.secret:default-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256}")
    private String jwtSecret;

    @Value("${app.security.jwt.access-token-ttl-minutes:30}")
    private long accessTokenTtlMinutes;

    @Value("${app.security.jwt.refresh-token-ttl-days:7}")
    private long refreshTokenTtlDays;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(AppUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("type", "access");
        claims.put("tokenVersion", user.getTokenVersion());
        if (user.getCustomer() != null) {
            claims.put("customerId", user.getCustomer().getId());
        } else {
            claims.put("customerId", null);
        }

        long expirationMs = accessTokenTtlMinutes * 60 * 1000;
        return createToken(claims, String.valueOf(user.getId()), expirationMs);
    }

    public String generateRefreshToken(AppUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("type", "refresh");

        long expirationMs = refreshTokenTtlDays * 24 * 60 * 60 * 1000;
        return createToken(claims, String.valueOf(user.getId()), expirationMs);
    }

    private String createToken(Map<String, Object> claims, String subject, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("JWT token is expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid JWT token");
        }
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public Long extractUserId(String token) {
        String sub = extractAllClaims(token).getSubject();
        return Long.parseLong(sub);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractType(String token) {
        return extractAllClaims(token).get("type", String.class);
    }

    public Integer extractTokenVersion(String token) {
        return extractAllClaims(token).get("tokenVersion", Integer.class);
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
    }
}
