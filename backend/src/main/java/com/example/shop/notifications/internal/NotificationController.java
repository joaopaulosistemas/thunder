package com.example.shop.notifications.internal;

import com.example.shop.notifications.api.NotificationView;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationStore store;

    NotificationController(NotificationStore store) {
        this.store = store;
    }

    /** Usuario comum so ve as proprias notificacoes; admin ve todas. */
    @GetMapping
    List<NotificationView> latest(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
        return store.latest().stream()
                .filter(n -> admin || n.customer().equals(authentication.getName()))
                .toList();
    }
}
