package com.ecommerce.controller;

import com.ecommerce.domain.Notification;
import com.ecommerce.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /**
     * Returns the notification audit trail for a given order.
     * Useful for debugging and customer support.
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Notification>> getByOrderId(@PathVariable String orderId) {
        log.info("[NOTIFICATION-SERVICE] GET /api/notifications/order/{}", orderId);
        List<Notification> notifications = notificationRepository.findByOrderId(orderId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/recipient/{email}")
    public ResponseEntity<List<Notification>> getByEmail(@PathVariable String email) {
        log.info("[NOTIFICATION-SERVICE] GET /api/notifications/recipient/{}", email);
        List<Notification> notifications = notificationRepository.findByRecipientEmail(email);
        return ResponseEntity.ok(notifications);
    }
}
