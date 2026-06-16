package com.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulates an external payment gateway (Stripe, PayPal, etc.).
 *
 * In a real system, this would make an HTTP call to the payment provider's API.
 * For this demo, it uses a configurable success rate and simulates network latency.
 *
 * Failure scenarios simulated:
 *   - Insufficient funds (amount > 1000)
 *   - Random decline (based on success-rate config)
 *   - Card expired (amount ends in .13)
 */
@Slf4j
@Component
public class PaymentGatewaySimulator {

    @Value("${payment.gateway.success-rate:0.8}")
    private double successRate;

    @Value("${payment.gateway.processing-delay-ms:500}")
    private long processingDelayMs;

    public GatewayResult charge(String orderId, BigDecimal amount, String currency) {
        log.info("[PAYMENT-SERVICE] Calling payment gateway | orderId={} | amount={} {}",
                orderId, amount, currency);

        simulateNetworkLatency();

        // ─── Business rule: high-value orders need extra validation ──────────
        if (amount.compareTo(new BigDecimal("1000")) > 0) {
            log.warn("[PAYMENT-SERVICE] Gateway: amount exceeds limit | amount={}", amount);
            return GatewayResult.failure(
                    "AMOUNT_EXCEEDS_LIMIT",
                    "Transaction amount exceeds single-payment limit of $1000"
            );
        }

        // ─── Simulate card expired for amounts ending in .13 ─────────────────
        if (amount.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.13")) == 0) {
            return GatewayResult.failure("CARD_EXPIRED", "Card has expired");
        }

        // ─── Random decline based on configured success rate ─────────────────
        if (Math.random() > successRate) {
            log.warn("[PAYMENT-SERVICE] Gateway: random decline | orderId={}", orderId);
            return GatewayResult.failure("INSUFFICIENT_FUNDS", "Payment declined by issuing bank");
        }

        String transactionId = "gw-txn-" + UUID.randomUUID().toString().substring(0, 12);
        log.info("[PAYMENT-SERVICE] Gateway: approved | transactionId={}", transactionId);
        return GatewayResult.success(transactionId);
    }

    private void simulateNetworkLatency() {
        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ─── Result Value Object ──────────────────────────────────────────────────

    public record GatewayResult(
            boolean approved,
            String transactionId,
            String failureCode,
            String failureReason
    ) {
        public static GatewayResult success(String transactionId) {
            return new GatewayResult(true, transactionId, null, null);
        }

        public static GatewayResult failure(String code, String reason) {
            return new GatewayResult(false, null, code, reason);
        }
    }
}
