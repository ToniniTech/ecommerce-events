package com.ecommerce.integration;

import com.ecommerce.IntegrationTestBase;
import com.ecommerce.service.OrderService;
import com.ecommerce.controller.dto.CreateOrderRequest;
import com.ecommerce.controller.dto.OrderResponse;
import com.ecommerce.domain.*;
import com.ecommerce.exception.DuplicateOrderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class OrderServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxEventRepository realOutboxEventRepository;

    @SpyBean private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        // Limpiar entre tests para independencia
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("should persist order and outbox event in the same transaction")
    void shouldPersistOrderAndOutboxEventTogether() {
        // Arrange
        CreateOrderRequest request = CreateOrderRequest.builder()
                .currency("USD")
                .idempotencyKey("idem-integration-001")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001")
                                .quantity(2)
                                .build()
                ))
                .build();

        // Act
        OrderResponse response = orderService.createOrder(
                "cust-test-001", "test@example.com", request);

        // Assert — verificar en MySQL real
        Optional<Order> savedOrder = orderRepository
                .findByOrderId(response.getOrderId());

        assertThat(savedOrder).isPresent();
        assertThat(savedOrder.get().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(savedOrder.get().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("259.98"));

        // Verificar que el OutboxEvent se creó en la misma transacción
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

    }

    @Test
    @DisplayName("should enforce idempotency — reject duplicate key in real DB")
    void shouldRejectDuplicateIdempotencyKeyInRealDatabase() {
        // Arrange — crear la primera orden
        CreateOrderRequest request = CreateOrderRequest.builder()
                .currency("USD")
                .idempotencyKey("idem-duplicate-test")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001").quantity(1).build()
                ))
                .build();

        orderService.createOrder("cust-001", "test@example.com", request);

        // Act & Assert — segunda orden con el mismo key debe fallar
        assertThatThrownBy(() ->
                orderService.createOrder("cust-001", "test@example.com", request))
                .isInstanceOf(DuplicateOrderException.class);

        // Verificar que solo hay 1 orden en la DB
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should rollback if outbox event fails")
    void shouldRollbackOrderWhenOutboxSaveFails(){
        //Arrange
        doThrow(new RuntimeException("Outbox DB failure"))
                .when(outboxEventRepository)
                .save(any(OutboxEvent.class));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .currency("USD")
                .idempotencyKey("idem-001")
                .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001")
                                .quantity(2)
                        .build()))
                .build();

        //Act & Assert
        assertThatThrownBy(()-> orderService
                        .createOrder("123",
                                "anthony09@gmail.com",
                                request))
                .isInstanceOf(Exception.class);

        //Verificar rollback
        assertThat(orderRepository.count()).isZero();
    }


}
