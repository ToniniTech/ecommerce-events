package com.ecommerce.controller;

import com.ecommerce.controller.dto.CreateOrderRequest;
import com.ecommerce.controller.dto.OrderResponse;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            Authentication authentication,     // ← injected by Spring Security from JWT
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequest request) {

        // customerId comes from JWT subject claim — set by JwtAuthenticationFilter
        String customerId = (String) authentication.getPrincipal();
        String customerEmail = (String) authentication.getDetails();

        log.info("[ORDER-SERVICE] POST /api/orders | customerId={} | items={}",
                customerId, request.getItems().size());

        OrderResponse response = orderService.createOrder(customerId, customerEmail, idempotencyKeyHeader, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            Authentication authentication,
            @PathVariable String orderId) {

        log.info("[ORDER-SERVICE] GET /api/orders/{} | requestedBy={}",
                orderId, authentication.getPrincipal());

        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
}
