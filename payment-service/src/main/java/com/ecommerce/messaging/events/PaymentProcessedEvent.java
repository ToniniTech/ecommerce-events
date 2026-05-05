package com.ecommerce.messaging.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published by Payment Service when a payment succeeds.
 * Consumed by: Order Service, Notification Service
 *
 * Routing Key: payment.processed
 * Exchange: payments.exchange
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProcessedEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @Builder.Default
    private String eventVersion = "v1";

    @Builder.Default
    private String eventType = "PAYMENT_PROCESSED";

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    // ── Business payload ──────────────────────────────────────────────────────

    private String paymentId;
    private String orderId;
    private String customerId;
    private String customerEmail;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String gatewayTransactionId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
}
