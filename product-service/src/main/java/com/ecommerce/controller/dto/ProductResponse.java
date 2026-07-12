package com.ecommerce.controller.dto;

import java.math.BigDecimal;

/**
 * Outbound representation of a product. Never expose the JPA entity directly.
 * Exposes the business key (not the internal technical id) plus the fields
 * order-service needs to check availability and price.
 */
public record ProductResponse(
        String productId,
        String name,
        BigDecimal price,
        Integer stock,
        boolean active
) {
}
