package com.ecommerce.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks already-processed events to guarantee idempotency.
 * If an event arrives twice (e.g., due to network retry), the consumer
 * checks this table and skips re-processing.
 */
@Entity
@Table(name = "processed_events",
       indexes = @Index(name = "idx_event_id", columnList = "event_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at")
    @CreationTimestamp
    private LocalDateTime processedAt;
}
