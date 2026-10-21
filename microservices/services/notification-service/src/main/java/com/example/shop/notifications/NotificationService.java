package com.example.shop.notifications;

import com.example.shop.common.inbox.Inbox;
import com.example.shop.contracts.OrderCancelled;
import com.example.shop.contracts.OrderConfirmed;
import com.example.shop.contracts.OrderRejected;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Este servico so conhece os eventos; aqui entraria o envio real (e-mail, push, SMS). */
@Service
public class NotificationService {

    static final String CONFIRMED = "notifications.order-confirmed";
    static final String REJECTED = "notifications.order-rejected";
    static final String CANCELLED = "notifications.order-cancelled";

    private final NotificationRepository repository;
    private final Inbox inbox;

    public NotificationService(NotificationRepository repository, Inbox inbox) {
        this.repository = repository;
        this.inbox = inbox;
    }

    @Transactional
    public void onConfirmed(OrderConfirmed event) {
        if (!inbox.firstTime(CONFIRMED, event.eventId())) {
            return;
        }
        String total = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(event.totalCents() / 100.0);
        repository.save(new Notification(event.orderId(), event.customer(),
                "Pedido #" + event.orderId() + " confirmado. Total " + total + "."));
    }

    @Transactional
    public void onRejected(OrderRejected event) {
        if (!inbox.firstTime(REJECTED, event.eventId())) {
            return;
        }
        repository.save(new Notification(event.orderId(), event.customer(),
                "Pedido #" + event.orderId() + " recusado: " + event.reason() + "."));
    }

    @Transactional
    public void onCancelled(OrderCancelled event) {
        if (!inbox.firstTime(CANCELLED, event.eventId())) {
            return;
        }
        repository.save(new Notification(event.orderId(), event.customer(),
                "Pedido #" + event.orderId() + " cancelado."));
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(String customer, boolean admin) {
        return admin ? repository.findTop50ByOrderByIdDesc() : repository.findTop50ByCustomerOrderByIdDesc(customer);
    }
}
