package com.ecommerce.service;

import com.ecommerce.controller.dto.*;
import com.ecommerce.domain.*;
import com.ecommerce.exception.*;
import com.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserSecurityService userSecurityService;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("[AUTH-SERVICE] Registering new user | email={}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.CUSTOMER)
                .build();

        user = userRepository.save(user);
        log.info("[AUTH-SERVICE] User registered | customerId={} | email={}",
                user.getCustomerId(), user.getEmail());

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {

        log.info("[AUTH-SERVICE] Login attempt | email={}", request.getEmail());


        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        try {
            // Spring Security validates email + password against DB
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            userSecurityService.registerFailedLogins(user.getEmail());
            log.warn("[AUTH-SERVICE] Failed login attempt | email={}", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Revoke all existing refresh tokens for this user (single-session policy)
        refreshTokenRepository.revokeAllByUserId(user.getId());

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        log.info("[AUTH-SERVICE] Login successful | customerId={}", user.getCustomerId());
        return buildAuthResponse(user, accessToken, refreshToken);
    }


    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.info("[AUTH-SERVICE] Token refresh requested");

        RefreshToken storedToken = refreshTokenRepository
                .findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!storedToken.isValid()) {
            log.warn("[AUTH-SERVICE] Refresh token is expired or revoked");
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        User user = storedToken.getUser();

        // Rotate refresh token — old one is revoked, new one is issued
         storedToken.setRevoked(true);//------> TESTING
         refreshTokenRepository.save(storedToken);

        String newAccessToken  = jwtService.generateAccessToken(user);
        String newRefreshToken = createRefreshToken(user);

        log.info("[AUTH-SERVICE] Token refreshed | customerId={}", user.getCustomerId());
        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String refreshToken) {

        RefreshToken token = refreshTokenRepository
                .findByRefreshToken(refreshToken)
                .orElseThrow(()-> new InvalidTokenException("Token not found"));

        if (!token.isValid()){
            throw new InvalidTokenException("Token is revoked or expired");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        log.info("[AUTH-SERVICE] User logged out | customerId={}",
                token.getUser().getCustomerId());
          //  log.info("[AUTH-SERVICE] User logged out | customerId={}", token.getUser().getCustomerId());

    }

    // -- Lock an account --------------------------------------------------------

    @Transactional
    public void lockUser(String customerId){
        //userRepository.findByCustomerId(customerId).ifPresent(user -> {
        User user = userRepository.findByCustomerId(customerId)
                        .orElseThrow(()-> new UserNotFoundException(
                                "User not found: " + customerId));
            user.setActive(false);
            refreshTokenRepository.revokeAllByUserId(user.getId());
            userRepository.save(user);
            log.info("[AUTH-SERVICE] User locked | customerId={}", user.getCustomerId());


    }

    // -- Unlock an account --------------------------------------------------------
    @Transactional
    public void unlockUser(String customerId){
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found" + customerId
                        ));
            user.setActive(true);
            userRepository.save(user);
            log.info("[AUTH SERVICE] User unlocked | customerId={}", user.getCustomerId());

    }

    // ── Validate Token (used by other microservices) ──────────────────────────

    public TokenValidationResponse validateToken(String token) {
        if (!jwtService.isTokenValid(token)) {
            return TokenValidationResponse.invalid();
        }
        return TokenValidationResponse.builder()
                .valid(true)
                .customerId(jwtService.extractCustomerId(token))
                .email(jwtService.extractEmail(token))
                .role(jwtService.extractRole(token))
                .expiresAt(jwtService.extractExpiration(token).toInstant().toString())
                .build();
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private String createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L))
                .build();
        return refreshTokenRepository.save(refreshToken).getRefreshToken();
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .customerId(user.getCustomerId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }
}
