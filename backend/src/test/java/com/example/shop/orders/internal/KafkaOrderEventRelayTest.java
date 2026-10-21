package com.example.shop.orders.internal;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.shop.orders.api.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaOrderEventRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendsJsonToOrdersCreatedTopic() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        KafkaOrderEventRelay relay = new KafkaOrderEventRelay(kafka, mapper);

        relay.onOrderCreated(new OrderCreatedEvent(7L, 1L, 2, 2000, "alice", Instant.parse("2026-10-18T00:00:00Z")));

        verify(kafka).send(org.mockito.ArgumentMatchers.eq("orders.created"),
                org.mockito.ArgumentMatchers.eq("7"), contains("\"customer\":\"alice\""));
    }
}
