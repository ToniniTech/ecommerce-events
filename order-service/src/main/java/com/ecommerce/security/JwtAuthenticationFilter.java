package com.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT filter for Order Service.
 *
 * Unlike Auth Service, Order Service does NOT load users from a database.
 * It only reads the JWT claims (customerId, email, role) and sets them
 * directly in the SecurityContext — stateless, zero DB lookups per request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (jwtService.isTokenValid(jwt) &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                String customerId = jwtService.extractCustomerId(jwt);
                String email      = jwtService.extractEmail(jwt);
                String role       = jwtService.extractRole(jwt);

                // Build authentication from JWT claims — no DB call needed
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                customerId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                // Store email as detail so controllers can access it
                authToken.setDetails(email);
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("[ORDER-SERVICE] JWT authenticated | customerId={} | role={}", customerId, role);
            }
        } catch (Exception ex) {
            log.error("[ORDER-SERVICE] JWT filter error: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
