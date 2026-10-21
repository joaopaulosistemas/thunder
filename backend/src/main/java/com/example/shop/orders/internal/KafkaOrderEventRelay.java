package com.example.shop.orders.internal;

import com.example.shop.orders.api.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ponte entre o evento de dominio e o Kafka. So publica depois do commit,
 * para nunca anunciar um pedido que acabou sofrendo rollback.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
class KafkaOrderEventRelay {

    static final String TOPIC = "orders.created";

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventRelay.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    KafkaOrderEventRelay(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onOrderCreated(OrderCreatedEvent event) {
        try {
            kafka.send(TOPIC, String.valueOf(event.orderId()), mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar evento do pedido {}", event.orderId(), e);
        }
    }
}
