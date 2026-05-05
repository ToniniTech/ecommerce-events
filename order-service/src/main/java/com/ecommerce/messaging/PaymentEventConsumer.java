package com.ecommerce.messaging;

import com.ecommerce.domain.ProcessedEvent;
import com.ecommerce.domain.ProcessedEventRepository;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import com.ecommerce.service.OrderService;
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
 * Consumes payment events from Payment Service.
 *
 * Idempotency guarantee:
 *   Before processing, we check if the eventId already exists in processed_events.
 *   If yes → ack immediately (skip). If no → process + record.
 *
 * Manual ACK is used so we control exactly when a message is acknowledged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    @RabbitListener(queues = "${rabbitmq.queue.payment-processed}",
                    containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void handlePaymentProcessed(
            PaymentProcessedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[ORDER-SERVICE] Received PaymentProcessed event | orderId={} | paymentId={} | eventId={}",
                event.getOrderId(), event.getPaymentId(), event.getEventId());

        // ── Idempotency check ─────────────────────────────────────────────────
        if (isAlreadyProcessed(event.getEventId())) {
            log.warn("[ORDER-SERVICE] Duplicate PaymentProcessed event detected, skipping | eventId={}",
                    event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            orderService.handlePaymentProcessed(event);
            recordProcessedEvent(event.getEventId(), "PAYMENT_PROCESSED");

            channel.basicAck(deliveryTag, false);
            log.info("[ORDER-SERVICE] PaymentProcessed handled and ACKed | orderId={}", event.getOrderId());

        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted same eventId — treat as duplicate
            log.warn("[ORDER-SERVICE] Race condition on idempotency key, treating as duplicate | eventId={}",
                    event.getEventId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[ORDER-SERVICE] Failed to handle PaymentProcessed event | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            // NACK without requeue → goes to DLQ after retry exhaustion
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment-failed}",
                    containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void handlePaymentFailed(
            PaymentFailedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[ORDER-SERVICE] Received PaymentFailed event | orderId={} | reason={} | eventId={}",
                event.getOrderId(), event.getFailureReason(), event.getEventId());

        if (isAlreadyProcessed(event.getEventId())) {
            log.warn("[ORDER-SERVICE] Duplicate PaymentFailed event detected, skipping | eventId={}",
                    event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            orderService.handlePaymentFailed(event);
            recordProcessedEvent(event.getEventId(), "PAYMENT_FAILED");

            channel.basicAck(deliveryTag, false);
            log.info("[ORDER-SERVICE] PaymentFailed handled and ACKed | orderId={}", event.getOrderId());

        } catch (Exception ex) {
            log.error("[ORDER-SERVICE] Failed to handle PaymentFailed event | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    private void recordProcessedEvent(String eventId, String eventType) {
        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .build()
        );
    }
}
