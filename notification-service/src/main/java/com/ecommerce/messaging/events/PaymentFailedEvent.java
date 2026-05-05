package com.ecommerce.messaging.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentFailedEvent {
    private String eventId;
    private String eventVersion;
    private String eventType;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;
    private String paymentId;
    private String orderId;
    private String customerId;
    private String customerEmail;
    private BigDecimal amount;
    private String currency;
    private String failureReason;
    private String failureCode;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime failedAt;
}
