package com.ecommerce.integration;

import com.ecommerce.IntegrationTestBase;
import com.ecommerce.config.RabbitMQConfig;
import com.ecommerce.domain.OutboxEvent;
import com.ecommerce.domain.OutboxEventRepository;
import com.ecommerce.domain.OutboxStatus;
import com.ecommerce.messaging.OutboxProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@ActiveProfiles("test")
@DisplayName("OutboxProcessor Integration Tests")
class OutboxProcessorIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessor outboxProcessor;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitMQConfig rabbitMQConfig;


    @Test
    @DisplayName("should process PENDING outbox events and mark them as PROCESSED")
    void shouldProcessPendingOutboxEvents() {
        // Arrange — insertar un evento pendiente directamente en la DB
        OutboxEvent pendingEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ORDER_CREATED")
                .payload("{\"orderId\":\"ord-outbox-test\",\"customerId\":\"cust-001\"}")
                .status(OutboxStatus.PENDING)
                .build();

        outboxEventRepository.save(pendingEvent);

        // Act — el processor lo recoge y publica
        outboxProcessor.process();

        // Assert — el evento debe estar marcado como PROCESSED en MySQL
        OutboxEvent processed = outboxEventRepository
                .findById(pendingEvent.getId())
                .orElseThrow();

        assertThat(processed.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        //assertThat(processed.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("should not reprocess already PROCESSED outbox events")
    void shouldNotReprocessAlreadyProcessedEvents() {
        // Arrange
        OutboxEvent alreadyProcessed = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ORDER_CREATED")
                .payload("{\"orderId\":\"ord-already-done\"}")
                .status(OutboxStatus.PUBLISHED)
                .build();

        OutboxEvent saved = outboxEventRepository.save(alreadyProcessed);

        // Act
        outboxProcessor.process();

        // Assert — el RabbitMQ no debe haber recibido nada
        OutboxEvent result = outboxEventRepository.findById(saved.getId())
                        .orElseThrow();

        assertThat(result.getStatus()).isEqualTo(saved.getStatus());




    }
}