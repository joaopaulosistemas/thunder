package com.example.shop.notifications;

import com.example.shop.contracts.OrderCancelled;
import com.example.shop.contracts.OrderConfirmed;
import com.example.shop.contracts.OrderRejected;
import com.example.shop.contracts.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class OrderEventListeners {

    private final ObjectMapper mapper;
    private final NotificationService service;

    OrderEventListeners(ObjectMapper mapper, NotificationService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @KafkaListener(topics = Topics.ORDER_CONFIRMED)
    void onConfirmed(String payload) throws JsonProcessingException {
        service.onConfirmed(mapper.readValue(payload, OrderConfirmed.class));
    }

    @KafkaListener(topics = Topics.ORDER_REJECTED)
    void onRejected(String payload) throws JsonProcessingException {
        service.onRejected(mapper.readValue(payload, OrderRejected.class));
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED)
    void onCancelled(String payload) throws JsonProcessingException {
        service.onCancelled(mapper.readValue(payload, OrderCancelled.class));
    }
}
