package com.ecommerce.exception;

/** Thrown when trying to create a product whose productId already exists. Maps to HTTP 409. */
public class DuplicateProductException extends RuntimeException {
    public DuplicateProductException(String productId) {
        super("Product already exists | productId=" + productId);
    }
}
