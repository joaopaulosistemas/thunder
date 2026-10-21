package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** Pedido criado, ainda sem estoque reservado. */
public record OrderCreated(UUID eventId, Instant occurredAt, long orderId, long productId, int quantity, String customer)
        implements DomainEvent {
}
