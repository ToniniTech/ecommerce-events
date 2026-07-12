package com.ecommerce.exception;

/**
 * Thrown when product-service cannot be reached or returns a server error
 * (connection refused, timeout, 5xx). Maps to HTTP 503, signalling a transient
 * downstream failure the client may retry — distinct from a business error.
 */
public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
