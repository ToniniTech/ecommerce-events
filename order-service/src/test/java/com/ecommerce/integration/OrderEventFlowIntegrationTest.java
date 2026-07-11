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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
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


    @BeforeEach
    void setUp() {
        // Limpiar entre tests para independencia
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
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

        // Act — crear la orden (esto guarda en Outbox, no publica directo)
        OrderResponse response = orderService.createOrder(
                "cust-event-001", "event@test.com", "idk-001", request);

        // Disparar el OutboxProcessor manualmente
        // (en producción lo hace el @Scheduled cada pocos segundos)
        outboxProcessor.process();

        // Esperar un momento para que RabbitMQ procese
        // En tests reales usarías Awaitility en lugar de Thread.sleep
        Thread.sleep(500);

        // Assert — verificar que el mensaje llegó a la queue real
        Message message = rabbitTemplate.receive(
                "order.created.queue",
                1000  // timeout 2 segundos
        );

        assertThat(message).isNotNull();

        // Deserializar el evento del mensaje
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(
                    message.getBody(), OrderCreatedEvent.class);
        } catch (IOException e) {
            throw new AssertionError(
                    "No se pudo deserializar OrderCreatedEvent", e);

        }

        assertThat(event.getOrderId()).isEqualTo(response.getOrderId());
        assertThat(event.getCustomerId()).isEqualTo("cust-event-001");
        assertThat(event.getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("129.99"));
    }

    @Test
    @DisplayName("should update order to PAID when PaymentProcessed event arrives")
    void shouldUpdateOrderStatusWhenPaymentProcessedEventArrives() {
        // Arrange — crear una orden primero
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-002").quantity(1).build()
                ))
                .build();

        OrderResponse created = orderService.createOrder(
                "cust-payment-001", "payment@test.com", "idk-001", request);

        // Act — simular que el payment-service publicó PaymentProcessed
        PaymentProcessedEvent paymentEvent = PaymentProcessedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(created.getOrderId())
                .paymentId("pay-integration-001")
                .amount(new BigDecimal("49.99"))
                .currency("USD")
                .processedAt(LocalDateTime.now())
                .build();

        // Publicar el evento directamente en RabbitMQ real
        rabbitTemplate.convertAndSend(
                "payments.exchange",
                "payment.processed",
                paymentEvent
        );

        // Esperar con Awaitility — mucho mejor que Thread.sleep
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