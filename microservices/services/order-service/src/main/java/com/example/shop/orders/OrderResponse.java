package com.example.shop.orders;

import java.time.Instant;

public record OrderResponse(long id, String customer, long productId, int quantity, long totalCents,
        OrderStatus status, Instant createdAt) {

    static OrderResponse of(Order o) {
        return new OrderResponse(o.getId(), o.getCustomer(), o.getProductId(), o.getQuantity(), o.getTotalCents(),
                o.getStatus(), o.getCreatedAt());
    }
}
