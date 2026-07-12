package com.ecommerce.exception;

/** Thrown when a stock adjustment would drive stock below zero. Maps to HTTP 409. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId, int current, int delta) {
        super("Insufficient stock | productId=" + productId
                + " | current=" + current + " | delta=" + delta);
    }
}
