package com.ecommerce.exception;

/** Thrown when a productId does not resolve to an existing product. Maps to HTTP 404. */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productId) {
        super("Product not found | productId=" + productId);
    }
}
