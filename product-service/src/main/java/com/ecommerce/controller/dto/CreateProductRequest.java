package com.ecommerce.controller.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inbound payload for creating a product.
 * {@code active} is optional and defaults to true when omitted.
 */
public record CreateProductRequest(

        @NotBlank(message = "productId is required")
        @Size(max = 64, message = "productId must be at most 64 characters")
        String productId,

        @NotBlank(message = "name is required")
        @Size(min = 3, max = 150, message = "name must be between 3 and 150 characters")
        String name,

        // inclusive = false enforces strictly greater than 0 (no zero, no negatives).
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimals")
        BigDecimal price,

        @NotNull(message = "stock is required")
        @Min(value = 0, message = "stock cannot be negative")
        Integer stock,

        // Nullable on purpose: defaults to true in the mapper when not provided.
        Boolean active
) {
}
