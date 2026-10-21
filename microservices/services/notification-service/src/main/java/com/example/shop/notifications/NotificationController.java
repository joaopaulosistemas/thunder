package com.example.shop.notifications;

import com.example.shop.common.security.Identity;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class NotificationController {

    record NotificationView(long id, long orderId, String customer, String message, Instant createdAt) {
    }

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/notifications")
    List<NotificationView> list(Authentication auth) {
        return service.recent(auth.getName(), Identity.isAdmin(auth)).stream()
                .map(n -> new NotificationView(n.getId(), n.getOrderId(), n.getCustomer(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
