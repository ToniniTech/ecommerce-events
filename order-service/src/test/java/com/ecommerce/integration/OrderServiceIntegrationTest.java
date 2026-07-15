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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

public class OrderServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;

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
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001")
                                .quantity(2)
                                .build()
                ))
                .build();

        // Act
        OrderResponse response = orderService.createOrder(
                "cust-test-001", "test@example.com", "idk-001", request);

        // Assert — Verify in real MySQL
        Optional<Order> savedOrder = orderRepository
                .findByOrderId(response.getOrderId());

        assertThat(savedOrder).isPresent();
        assertThat(savedOrder.get().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(savedOrder.get().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("259.98"));

        // Verify that the OutboxEvent was created within the same transaction.
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(outboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

    }

    @Test
    @DisplayName("should enforce idempotency — reject duplicate key in real DB")
    void shouldRejectDuplicateIdempotencyKeyInRealDatabase() {
        // Arrange — create the first order
        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("prod-001").quantity(1).build()
                ))
                .build();

        orderService.createOrder("cust-001", "test@example.com", "idk-001", request);

        // Act & Assert — A second order with the same key must fail.
        assertThatThrownBy(() ->
                orderService.createOrder("cust-001", "test@example.com", "idk-001", request))
                .isInstanceOf(DuplicateOrderException.class);

        // Verify that there is only one order in the database.
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
                .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                                .quantity(2)
                                .productId("prod-001")
                        .build()))
                .build();

        //Act & Assert
        assertThatThrownBy(()->orderService.
                createOrder("123",
                        "anthony09@gmail.com",
                        "idk-001",
                        request))
                .isInstanceOf(Exception.class);

        //Verify rollback
        assertThat(orderRepository.count()).isZero();

    }



}
