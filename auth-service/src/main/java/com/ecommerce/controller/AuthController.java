package com.ecommerce.controller;

import com.ecommerce.controller.dto.*;
import com.ecommerce.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Body: { firstName, lastName, email, password }
     * Returns: accessToken + refreshToken + customerId
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("[AUTH-SERVICE] POST /api/auth/register | email={}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     * Body: { email, password }
     * Returns: accessToken + refreshToken + customerId
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AUTH-SERVICE] POST /api/auth/login | email={}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/refresh
     * Body: { refreshToken }
     * Returns: new accessToken + new refreshToken (token rotation)
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("[AUTH-SERVICE] POST /api/auth/refresh");
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/logout
     * Body: { refreshToken }
     * Revokes the refresh token — user must login again after access token expires
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        log.info("[AUTH-SERVICE] POST /api/auth/logout");
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/validate
     * Header: Authorization: Bearer <token>
     *
     * Used by other microservices to verify a token without sharing the secret.
     * Returns customerId + email + role so services don't need to decode JWT themselves.
     */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        TokenValidationResponse response = authService.validateToken(token);

        if (!response.isValid()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/auth/me
     * Header: Authorization: Bearer <token>
     * Returns the current user's profile from the token claims
     */
    @GetMapping("/me")
    public ResponseEntity<TokenValidationResponse> me(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.ok(authService.validateToken(token));
    }
}
