package com.example.shop.orders.internal;

import java.time.Instant;

record OrderResponse(Long id, Long productId, int quantity, long totalCents, String customer, Instant createdAt) {

    static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getProductId(), o.getQuantity(), o.getTotalCents(),
                o.getCustomer(), o.getCreatedAt());
    }
}
