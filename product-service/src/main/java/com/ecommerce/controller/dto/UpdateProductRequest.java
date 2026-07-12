package com.ecommerce.controller.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inbound payload for a full update of a product.
 *
 * <p>By design this record has NO productId field: the business key is immutable and
 * is taken from the path only. It also has NO stock field: stock is managed
 * exclusively through the PATCH /stock delta endpoint, which guarantees the
 * never-negative invariant and participates in optimistic locking.
 */
public record UpdateProductRequest(

        @NotBlank(message = "name is required")
        @Size(min = 3, max = 150, message = "name must be between 3 and 150 characters")
        String name,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimals")
        BigDecimal price,

        @NotNull(message = "active is required")
        Boolean active
) {
}
