package com.ecommerce.integration;

import com.ecommerce.IntegrationTestBase;
import com.ecommerce.service.OrderService;
import com.ecommerce.controller.dto.CreateOrderRequest;
import com.ecommerce.controller.dto.OrderResponse;
import com.ecommerce.domain.Order;
import com.ecommerce.domain.OrderRepository;
import com.ecommerce.domain.OrderStatus;
import com.ecommerce.domain.OutboxEventRepository;
import com.ecommerce.messaging.OutboxProcessor;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Order → Payment Event Flow Integration Tests")
class OrderEventFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxProcessor outboxProcessor;
    @Autowired
    private AmqpAdmin amqpAdmin;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());



    @BeforeEach
    void setUp() {
        // Clean among between tests for isolation
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        amqpAdmin.purgeQueue("order.created.queue", false);
    }


    @Test
    @DisplayName("should publish OrderCreated event to RabbitMQ after order creation")
    void shouldPublishOrderCreatedEventToRabbitMQ() throws InterruptedException {

        // Arrange
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001").quantity(1).build()
                ))
                .build();

        // Act — create the order (this saves it to the Outbox; it does not publish directly)
        OrderResponse response = orderService.createOrder(
                "cust-event-001", "event@test.com", "idk-001", request);


        // Manually trigger the OutboxProcessor
        // (in production, the @Scheduled task does this every few seconds)
        outboxProcessor.process();

        // Wait a moment for RabbitMQ to process
        // Assert — keep polling the queue until the message shows up (or we time out)
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Message message = rabbitTemplate.receive("order.created.queue");
                    assertThat(message).isNotNull();

                    OrderCreatedEvent event = objectMapper.readValue(
                            message.getBody(), OrderCreatedEvent.class);

                    assertThat(event.getOrderId()).isEqualTo(response.getOrderId());
                    assertThat(event.getCustomerId()).isEqualTo("cust-event-001");
                    assertThat(event.getTotalAmount())
                            .isEqualByComparingTo(new BigDecimal("129.99"));
                });
    }

    @Test
    @DisplayName("should update order to PAID when PaymentProcessed event arrives")
    void shouldUpdateOrderStatusWhenPaymentProcessedEventArrives() {
        // Arrange — create an order first
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-002").quantity(1).build()
                ))
                .build();

        OrderResponse created = orderService.createOrder(
                "cust-payment-001", "payment@test.com", "idk-001", request);

        // Act — simulate the published payment service "Payment Processed"
        PaymentProcessedEvent paymentEvent = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(created.getOrderId())
                .paymentId("pay-integration-001")
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .processedAt(LocalDateTime.now())
                .build();

        // Publish the event directly to real RabbitMQ
        rabbitTemplate.convertAndSend(
                "payments.exchange",
                "payment.processed",
                paymentEvent
        );

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Order order = orderRepository
                            .findByOrderId(created.getOrderId())
                            .orElseThrow();
                    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
                    assertThat(order.getPaymentId()).isEqualTo("pay-integration-001");
                });
    }


}