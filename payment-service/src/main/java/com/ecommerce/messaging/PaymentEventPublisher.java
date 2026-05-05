package com.ecommerce.messaging;

import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes payment domain events to the payments.exchange.
 * Both Order Service and Notification Service subscribe to these events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.payments}")
    private String paymentsExchange;

    @Value("${rabbitmq.routing-key.payment-processed}")
    private String paymentProcessedKey;

    @Value("${rabbitmq.routing-key.payment-failed}")
    private String paymentFailedKey;

    /**
     * Published when payment gateway approves the transaction.
     * Triggers: Order → PAID, Notification → success email
     */
    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        log.info("[PAYMENT-SERVICE] Publishing PaymentProcessed | orderId={} | paymentId={} | eventId={}",
                event.getOrderId(), event.getPaymentId(), event.getEventId());
        try {
            rabbitTemplate.convertAndSend(paymentsExchange, paymentProcessedKey, event);
            log.info("[PAYMENT-SERVICE] PaymentProcessed published | orderId={}", event.getOrderId());
        } catch (Exception ex) {
            log.error("[PAYMENT-SERVICE] Failed to publish PaymentProcessed | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to publish PaymentProcessed event", ex);
        }
    }

    /**
     * Published when payment gateway rejects the transaction.
     * Triggers: Order → PAYMENT_FAILED, Notification → failure email
     */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        log.info("[PAYMENT-SERVICE] Publishing PaymentFailed | orderId={} | reason={} | eventId={}",
                event.getOrderId(), event.getFailureReason(), event.getEventId());
        try {
            rabbitTemplate.convertAndSend(paymentsExchange, paymentFailedKey, event);
            log.info("[PAYMENT-SERVICE] PaymentFailed published | orderId={}", event.getOrderId());
        } catch (Exception ex) {
            log.error("[PAYMENT-SERVICE] Failed to publish PaymentFailed | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to publish PaymentFailed event", ex);
        }
    }
}
