package com.ecommerce.messaging;

import com.ecommerce.domain.ProcessedEvent;
import com.ecommerce.domain.ProcessedEventRepository;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import com.ecommerce.service.NotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Consumes all business events and triggers corresponding notifications.
 *
 * Three independent listeners — isolated queues mean a failure in one
 * notification type never blocks the processing of another.
 *
 * Idempotency key format: "notif:{eventType}:{eventId}"
 * This allows the same event to trigger multiple notification types
 * (e.g., ORDER_CREATED can trigger both order confirmation AND
 *  a separate internal alert) without collision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    // ─── Order Created → Order Confirmation Notification ─────────────────────

    @RabbitListener(
            queues = "${rabbitmq.queue.notification-order-created}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional
    public void handleOrderCreated(
            OrderCreatedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        String idempotencyKey = "notif:ORDER_CREATED:" + event.getEventId();
        log.info("[NOTIFICATION-SERVICE] Received OrderCreated | orderId={} | eventId={}",
                event.getOrderId(), event.getEventId());

        if (isAlreadyProcessed(idempotencyKey)) {
            log.warn("[NOTIFICATION-SERVICE] Duplicate OrderCreated notification skipped | key={}",
                    idempotencyKey);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            notificationService.sendOrderConfirmation(event);
            recordProcessedEvent(idempotencyKey, "ORDER_CREATED_NOTIFICATION");
            channel.basicAck(deliveryTag, false);
            log.info("[NOTIFICATION-SERVICE] Order confirmation sent | orderId={}", event.getOrderId());

        } catch (DataIntegrityViolationException e) {
            log.warn("[NOTIFICATION-SERVICE] Race condition on duplicate, ACKing | key={}", idempotencyKey);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[NOTIFICATION-SERVICE] Failed to send order confirmation | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ─── Payment Processed → Payment Success Notification ────────────────────

    @RabbitListener(
            queues = "${rabbitmq.queue.notification-payment-processed}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentProcessed(
            PaymentProcessedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        String idempotencyKey = "notif:PAYMENT_PROCESSED:" + event.getEventId();
        log.info("[NOTIFICATION-SERVICE] Received PaymentProcessed | orderId={} | eventId={}",
                event.getOrderId(), event.getEventId());

        if (isAlreadyProcessed(idempotencyKey)) {
            log.warn("[NOTIFICATION-SERVICE] Duplicate PaymentProcessed notification skipped | key={}",
                    idempotencyKey);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            notificationService.sendPaymentSuccess(event);
            recordProcessedEvent(idempotencyKey, "PAYMENT_SUCCESS_NOTIFICATION");
            channel.basicAck(deliveryTag, false);
            log.info("[NOTIFICATION-SERVICE] Payment success notification sent | orderId={}",
                    event.getOrderId());

        } catch (DataIntegrityViolationException e) {
            log.warn("[NOTIFICATION-SERVICE] Race condition on duplicate, ACKing | key={}", idempotencyKey);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[NOTIFICATION-SERVICE] Failed to send payment success notification | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ─── Payment Failed → Payment Failure Notification ───────────────────────

    @RabbitListener(
            queues = "${rabbitmq.queue.notification-payment-failed}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentFailed(
            PaymentFailedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        String idempotencyKey = "notif:PAYMENT_FAILED:" + event.getEventId();
        log.info("[NOTIFICATION-SERVICE] Received PaymentFailed | orderId={} | reason={} | eventId={}",
                event.getOrderId(), event.getFailureReason(), event.getEventId());

        if (isAlreadyProcessed(idempotencyKey)) {
            log.warn("[NOTIFICATION-SERVICE] Duplicate PaymentFailed notification skipped | key={}",
                    idempotencyKey);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            notificationService.sendPaymentFailure(event);
            recordProcessedEvent(idempotencyKey, "PAYMENT_FAILURE_NOTIFICATION");
            channel.basicAck(deliveryTag, false);
            log.info("[NOTIFICATION-SERVICE] Payment failure notification sent | orderId={}",
                    event.getOrderId());

        } catch (DataIntegrityViolationException e) {
            log.warn("[NOTIFICATION-SERVICE] Race condition on duplicate, ACKing | key={}", idempotencyKey);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[NOTIFICATION-SERVICE] Failed to send payment failure notification | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAlreadyProcessed(String idempotencyKey) {
        return processedEventRepository.existsByEventId(idempotencyKey);
    }

    private void recordProcessedEvent(String idempotencyKey, String eventType) {
        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(idempotencyKey)
                        .eventType(eventType)
                        .build()
        );
    }
}
