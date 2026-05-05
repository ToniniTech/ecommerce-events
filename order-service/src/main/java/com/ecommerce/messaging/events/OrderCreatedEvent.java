package com.ecommerce.messaging.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Event published by Order Service when a new order is created.
 * Consumed by: Payment Service, Notification Service
 *
 * Version: v1
 * Routing Key: order.created
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {

    /** Unique event identifier — used for idempotency checks */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /** Event schema version for future compatibility */
    @Builder.Default
    private String eventVersion = "v1";

    /** Event type discriminator */
    @Builder.Default
    private String eventType = "ORDER_CREATED";

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

    // ---- Business payload ----

    private String orderId;
    private String customerId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private String currency;
    private List<OrderItemDto> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemDto {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
