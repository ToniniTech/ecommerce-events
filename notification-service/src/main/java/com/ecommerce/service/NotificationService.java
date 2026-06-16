package com.ecommerce;

import com.ecommerce.domain.*;
import com.ecommerce.messaging.events.OrderCreatedEvent;
import com.ecommerce.messaging.events.PaymentFailedEvent;
import com.ecommerce.messaging.events.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles all notification dispatch logic.
 *
 * Each method:
 * 1. Builds the Notification record and persists it (PENDING)
 * 2. Attempts to send via email (real or simulated)
 * 3. Updates the record to SENT or FAILED
 *
 * The notification record serves as an audit log for all communications.
 * Even when mail.enabled=false, every notification is recorded in the DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailTemplateBuilder templateBuilder;
    private final JavaMailSender mailSender;

    @Value("${notification.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${notification.mail.from:noreply@ecommerce.com}")
    private String fromAddress;

    // ─── Order Confirmation ───────────────────────────────────────────────────

    @Transactional
    public void sendOrderConfirmation(OrderCreatedEvent event) {
        log.info("[NOTIFICATION-SERVICE] Sending ORDER_CONFIRMATION | orderId={} | to={}",
                event.getOrderId(), event.getCustomerEmail());

        String subject = templateBuilder.buildOrderConfirmationSubject(event.getOrderId());
        String body    = templateBuilder.buildOrderConfirmationBody(event);

        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID().toString())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .recipientEmail(event.getCustomerEmail())
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .status(NotificationStatus.PENDING)
                .subject(subject)
                .body(body)
                .triggeredByEventId(event.getEventId())
                .build();

        notification = notificationRepository.save(notification);
        dispatchEmail(notification, event.getCustomerEmail(), subject, body);
    }

    // ─── Payment Success ──────────────────────────────────────────────────────

    @Transactional
    public void sendPaymentSuccess(PaymentProcessedEvent event) {
        log.info("[NOTIFICATION-SERVICE] Sending PAYMENT_SUCCESS | orderId={} | to={}",
                event.getOrderId(), event.getCustomerEmail());

        String subject = templateBuilder.buildPaymentSuccessSubject(event.getOrderId());
        String body    = templateBuilder.buildPaymentSuccessBody(event);

        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID().toString())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .recipientEmail(event.getCustomerEmail())
                .notificationType(NotificationType.PAYMENT_SUCCESS)
                .status(NotificationStatus.PENDING)
                .subject(subject)
                .body(body)
                .triggeredByEventId(event.getEventId())
                .build();

        notification = notificationRepository.save(notification);
        dispatchEmail(notification, event.getCustomerEmail(), subject, body);
    }

    // ─── Payment Failure ──────────────────────────────────────────────────────

    @Transactional
    public void sendPaymentFailure(PaymentFailedEvent event) {
        log.info("[NOTIFICATION-SERVICE] Sending PAYMENT_FAILURE | orderId={} | to={}",
                event.getOrderId(), event.getCustomerEmail());

        String subject = templateBuilder.buildPaymentFailureSubject(event.getOrderId());
        String body    = templateBuilder.buildPaymentFailureBody(event);

        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID().toString())
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .recipientEmail(event.getCustomerEmail())
                .notificationType(NotificationType.PAYMENT_FAILURE)
                .status(NotificationStatus.PENDING)
                .subject(subject)
                .body(body)
                .triggeredByEventId(event.getEventId())
                .build();

        notification = notificationRepository.save(notification);
        dispatchEmail(notification, event.getCustomerEmail(), subject, body);
    }

    // ─── Internal Dispatch ────────────────────────────────────────────────────

    private void dispatchEmail(Notification notification,
                                String recipient,
                                String subject,
                                String body) {
        if (mailEnabled) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);

                notification.markAsSent();
                notificationRepository.save(notification);

                log.info("[NOTIFICATION-SERVICE] Email sent | notificationId={} | to={}",
                        notification.getNotificationId(), recipient);
            } catch (Exception ex) {
                notification.markAsFailed(ex.getMessage());
                notificationRepository.save(notification);
                log.error("[NOTIFICATION-SERVICE] Email send FAILED | notificationId={} | error={}",
                        notification.getNotificationId(), ex.getMessage(), ex);
                throw ex; // rethrow so consumer can NACK and retry
            }
        } else {
            // Simulation mode — log as if sent, mark as SENT in DB for demo
            log.info("""
                    [NOTIFICATION-SERVICE] [SIMULATED EMAIL]
                    ════════════════════════════════════════
                    TO      : {}
                    SUBJECT : {}
                    ────────────────────────────────────────
                    {}
                    ════════════════════════════════════════
                    """, recipient, subject, body);

            notification.markAsSent();
            notificationRepository.save(notification);
        }
    }
}
