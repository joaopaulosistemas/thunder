package com.example.shop.orders;

import com.example.shop.contracts.ProductChanged;
import com.example.shop.contracts.StockRejected;
import com.example.shop.contracts.StockReserved;
import com.example.shop.contracts.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class SagaListeners {

    private final ObjectMapper mapper;
    private final OrderSaga saga;

    SagaListeners(ObjectMapper mapper, OrderSaga saga) {
        this.mapper = mapper;
        this.saga = saga;
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    void onReserved(String payload) throws JsonProcessingException {
        saga.onStockReserved(mapper.readValue(payload, StockReserved.class));
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED)
    void onRejected(String payload) throws JsonProcessingException {
        saga.onStockRejected(mapper.readValue(payload, StockRejected.class));
    }

    @KafkaListener(topics = Topics.PRODUCT_CHANGED)
    void onProductChanged(String payload) throws JsonProcessingException {
        saga.onProductChanged(mapper.readValue(payload, ProductChanged.class));
    }
}
