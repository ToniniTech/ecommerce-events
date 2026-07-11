package com.ecommerce.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * Request body para POST /api/orders
 *
 * Lo que el cliente ENVÍA:
 *   - customerEmail  → quién hace el pedido
 *   - currency       → moneda (USD, EUR, ARS)
 *   - idempotencyKey → opcional, protección contra doble-click en el frontend
 *   - items          → solo productId + quantity (sin precio)
 *
 * Lo que el cliente NO envía (el servidor lo resuelve):
 *   - customerId  → extraído del header X-Customer-Id (simula extracción de JWT)
 *   - productName → resuelto desde ProductCatalogService
 *   - unitPrice   → resuelto desde ProductCatalogService (el cliente nunca decide el precio)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {

        @NotBlank(message = "productId is required")
        private String productId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}
