package com.ecommerce.messaging;

import com.ecommerce.messaging.events.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events produced by the Order Service.
 * Uses RabbitTemplate with JSON converter — no HTTP calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.orders}")
    private String ordersExchange;

    @Value("${rabbitmq.routing-key.order-created}")
    private String orderCreatedRoutingKey;

    /**
     * Publishes an OrderCreated event to the orders exchange.
     * Payment Service and Notification Service are subscribed to this event.
     *
     * @param event the domain event with full order details
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("[ORDER-SERVICE] Publishing OrderCreated event | orderId={} | eventId={}",
                event.getOrderId(), event.getEventId());

        try {
            rabbitTemplate.convertAndSend(ordersExchange, orderCreatedRoutingKey, event);
            log.info("[ORDER-SERVICE] OrderCreated published successfully | orderId={}", event.getOrderId());
        } catch (Exception ex) {
            log.error("[ORDER-SERVICE] Failed to publish OrderCreated event | orderId={} | error={}",
                    event.getOrderId(), ex.getMessage(), ex);
            // In production: implement outbox pattern here to guarantee delivery
            throw new RuntimeException("Failed to publish OrderCreated event", ex);
        }
    }
}
