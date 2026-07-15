package com.ecommerce.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, nullable = false, updatable = false)
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.orderId == null) {
            this.orderId = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void markAsPaymentProcessing() {
        this.status = OrderStatus.PAYMENT_PROCESSING;
    }

    public void markAsPaid(String paymentId) {
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
    }

    public void markAsPaymentFailed(String reason) {
        this.status = OrderStatus.PAYMENT_FAILED;
        this.failureReason = reason;
    }

    public void markAsCancelled() {
        this.status = OrderStatus.CANCELLED;
    }
}
