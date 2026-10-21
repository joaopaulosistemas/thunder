package com.example.shop.catalog;

import com.example.shop.contracts.OrderCreated;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consome orders.created e orders.stock-release (compensacao). O servico conhece so o JSON do contrato, nao as classes do order-service. */
@Component
class OrderCreatedListener {

    private final ObjectMapper mapper;
    private final StockService stock;

    OrderCreatedListener(ObjectMapper mapper, StockService stock) {
        this.mapper = mapper;
        this.stock = stock;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    void onMessage(String payload) throws JsonProcessingException {
        stock.onOrderCreated(mapper.readValue(payload, OrderCreated.class));
    }

    @KafkaListener(topics = Topics.STOCK_RELEASE)
    void onRelease(String payload) throws JsonProcessingException {
        stock.onStockRelease(mapper.readValue(payload, StockReleaseRequested.class));
    }
}
