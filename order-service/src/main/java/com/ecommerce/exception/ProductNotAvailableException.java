package com.ecommerce.exception;

/**
 * Thrown when a product exists in the catalog but is not purchasable
 * (active = false, i.e. soft-deleted/hidden). Maps to HTTP 422: the request is
 * well-formed but cannot be fulfilled for a business reason.
 */
public class ProductNotAvailableException extends RuntimeException {
    public ProductNotAvailableException(String message) {
        super(message);
    }
}