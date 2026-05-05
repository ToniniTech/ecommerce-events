package com.ecommerce.service;

import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Builds human-readable HTML email bodies for each notification type.
 * In production, replace with Thymeleaf templates or a template engine.
 */
@Component
public class EmailTemplateBuilder {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public String buildOrderConfirmationSubject(String orderId) {
        return String.format("✅ Order Confirmed — #%s", orderId);
    }

    public String buildOrderConfirmationBody(OrderCreatedEvent event) {
        StringBuilder items = new StringBuilder();
        if (event.getItems() != null) {
            event.getItems().forEach(item ->
                items.append(String.format(
                    "  • %s (x%d) — %s %.2f%n",
                    item.getProductName(),
                    item.getQuantity(),
                    event.getCurrency(),
                    item.getSubtotal()
                ))
            );
        }

        return String.format("""
                Hello,

                Your order has been received and is being processed.

                ─────────────────────────────────
                Order ID  : %s
                Date      : %s
                ─────────────────────────────────
                Items:
                %s
                ─────────────────────────────────
                TOTAL     : %s %.2f
                ─────────────────────────────────

                We'll notify you as soon as your payment is confirmed.

                Thank you for shopping with us!
                """,
                event.getOrderId(),
                event.getOccurredAt() != null ? event.getOccurredAt().format(FMT) : "N/A",
                items,
                event.getCurrency(),
                event.getTotalAmount()
        );
    }

    public String buildPaymentSuccessSubject(String orderId) {
        return String.format("💳 Payment Confirmed — Order #%s", orderId);
    }

    public String buildPaymentSuccessBody(PaymentProcessedEvent event) {
        return String.format("""
                Hello,

                Great news! Your payment has been successfully processed.

                ─────────────────────────────────
                Order ID      : %s
                Payment ID    : %s
                Amount        : %s %.2f
                Method        : %s
                Transaction   : %s
                Processed at  : %s
                ─────────────────────────────────

                Your order is now confirmed and will be prepared for shipment.

                Thank you for your purchase!
                """,
                event.getOrderId(),
                event.getPaymentId(),
                event.getCurrency(),
                event.getAmount(),
                event.getPaymentMethod() != null ? event.getPaymentMethod() : "CREDIT_CARD",
                event.getGatewayTransactionId(),
                event.getProcessedAt() != null ? event.getProcessedAt().format(FMT) : "N/A"
        );
    }

    public String buildPaymentFailureSubject(String orderId) {
        return String.format("❌ Payment Failed — Order #%s", orderId);
    }

    public String buildPaymentFailureBody(PaymentFailedEvent event) {
        return String.format("""
                Hello,

                Unfortunately, your payment could not be processed.

                ─────────────────────────────────
                Order ID      : %s
                Amount        : %s %.2f
                Reason        : %s
                Failed at     : %s
                ─────────────────────────────────

                What you can do:
                  1. Check your card details and available balance
                  2. Try a different payment method
                  3. Contact your bank if the issue persists

                Please visit our website to retry your order.

                We apologize for the inconvenience.
                """,
                event.getOrderId(),
                event.getCurrency(),
                event.getAmount(),
                event.getFailureReason(),
                event.getFailedAt() != null ? event.getFailedAt().format(FMT) : "N/A"
        );
    }
}
