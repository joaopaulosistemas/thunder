package com.example.shop.notifications.internal;

import com.example.shop.notifications.api.NotificationView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Memoria simples das ultimas notificacoes (demonstracao; em producao seria um outbox/DB). */
@Component
class NotificationStore {

    private static final int MAX = 20;

    private final LinkedList<NotificationView> items = new LinkedList<>();

    /**
     * Idempotente por pedido: o Kafka entrega "ao menos uma vez", entao o mesmo evento pode
     * chegar de novo (rebalance, retry). Reentrega substitui a notificacao em vez de duplicar.
     */
    synchronized void add(NotificationView view) {
        items.removeIf(existing -> existing.orderId().equals(view.orderId()));
        items.addFirst(view);
        while (items.size() > MAX) {
            items.removeLast();
        }
    }

    synchronized List<NotificationView> latest() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }
}
