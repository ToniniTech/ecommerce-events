package com.ecommerce.messaging;

import com.ecommerce.domain.ProcessedEvent;
import com.ecommerce.domain.ProcessedEventRepository;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.service.PaymentService;
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
 * Consumes OrderCreated events from orders.exchange.
 *
 * Core responsibilities:
 * 1. Idempotency check — avoids double-charging the customer
 * 2. Delegate to PaymentService for actual processing
 * 3. Manual ACK/NACK control — messages go to DLQ on persistent failures
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;

    @RabbitListener(
            queues = "${rabbitmq.queue.order-created}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    @Transactional
    public void handleOrderCreated(
            OrderCreatedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("[PAYMENT-SERVICE] Received OrderCreated | orderId={} | amount={} {} | eventId={}",
                event.getOrderId(), event.getTotalAmount(), event.getCurrency(), event.getEventId());

        // ─── Idempotency Guard ────────────────────────────────────────────────
        // Critical: prevents charging the customer twice if the message is redelivered
        if (isAlreadyProcessed(event.getEventId())) {
            log.warn("[PAYMENT-SERVICE] Duplicate OrderCreated detected — skipping | eventId={}",
                    event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            paymentService.processPayment(event);
            recordProcessedEvent(event.getEventId(), "ORDER_CREATED");

            channel.basicAck(deliveryTag, false);
            log.info("[PAYMENT-SERVICE] OrderCreated processed and ACKed | orderId={}", event.getOrderId());

        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent thread processed same event — safe to ACK
            log.warn("[PAYMENT-SERVICE] Concurrent duplicate event, ACKing | eventId={}", event.getEventId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[PAYMENT-SERVICE] Failed to process OrderCreated | orderId={} | attempt will retry | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            // NACK without requeue — retry interceptor handles retries, then sends to DLQ
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
