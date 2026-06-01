package com.ecommerce.messaging;


import com.ecommerce.domain.OutboxEvent;
import com.ecommerce.domain.OutboxEventRepository;
import com.ecommerce.domain.OutboxStatus;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)

    public void process(){
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                OrderCreatedEvent event = objectMapper.readValue(
                        outboxEvent.getPayload(), OrderCreatedEvent.class
                );
                eventPublisher.publishOrderCreated(event);
                outboxEvent.setStatus(OutboxStatus.PUBLISHED);
                outboxEventRepository.save(outboxEvent);
                log.info("[OUTBOX] Event published | eventId={}", outboxEvent.getEventId());
            } catch (Exception e) {
                log.error("[OUTBOX] Failed to publish event | eventId={} | error={}",
                        outboxEvent.getEventId(), e.getMessage());
            }
        }
    }
}
