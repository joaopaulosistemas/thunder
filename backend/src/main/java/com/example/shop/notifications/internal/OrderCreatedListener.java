package com.example.shop.notifications.internal;

import com.example.shop.notifications.api.NotificationView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consome o topico orders.created. Depende apenas do contrato JSON do topico,
 * nunca de classes do modulo orders: assim pode virar um servico separado.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final NotificationStore store;
    private final ObjectMapper mapper;
    private final Counter processed;

    OrderCreatedListener(NotificationStore store, ObjectMapper mapper, MeterRegistry registry) {
        this.store = store;
        this.mapper = mapper;
        this.processed = Counter.builder("shop.notifications.processed")
                .description("Eventos de pedido processados").register(registry);
    }

    @KafkaListener(topics = "orders.created", groupId = "notifications")
    void onMessage(String payload) throws java.io.IOException {
        handle(payload);
    }

    void handle(String payload) throws java.io.IOException {
        JsonNode json = mapper.readTree(payload);
        long orderId = json.path("orderId").asLong();
        long total = json.path("totalCents").asLong();
        String customer = json.path("customer").asText("anonymous");
        String message = "Pedido #" + orderId + " de " + customer
                + " confirmado (R$ " + String.format("%.2f", total / 100.0) + ")";
        store.add(new NotificationView(orderId, customer, message));
        processed.increment();
        log.info("Notificacao gerada para o pedido {}", orderId);
    }
}
