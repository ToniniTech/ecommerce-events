package com.ecommerce.security;

import com.ecommerce.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles all JWT operations: generation, validation, claims extraction.
 *
 * JWT structure:
 * Header: { "alg": "HS256", "typ": "JWT" }
 * Payload: {
 *   "sub":         "cust-550e8400",       ← customerId (stable public ID)
 *   "email":       "user@example.com",
 *   "role":        "CUSTOMER",
 *   "firstName":   "Juan",
 *   "iat":         1704067200,             ← issued at
 *   "exp":         1704153600              ← expires at (24h later)
 * }
 * Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    // ── Token Generation ──────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email",     user.getEmail());
        claims.put("role",      user.getRole().name());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName",  user.getLastName());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getCustomerId())   // sub = customerId, NOT email
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("[AUTH-SERVICE] Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    // ── Claims Extraction ─────────────────────────────────────────────────────

    /**
     * Extracts the customerId from the JWT subject claim.
     * This is what other microservices use to identify the caller.
     */
    public String extractCustomerId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(getClaims(token));
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
