package com.ecommerce.client;

import java.math.BigDecimal;

/**
 * Minimal, already-validated product data that order-service actually needs to
 * build an order line: the authoritative name and price resolved from the catalog.
 * Stock/active are consumed inside the client and not leaked further.
 */
public record ProductInfo(
        String productId,
        String name,
        BigDecimal price
) {
}
