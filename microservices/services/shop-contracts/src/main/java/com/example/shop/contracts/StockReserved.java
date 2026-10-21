package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** O catalogo reservou o estoque do pedido. */
public record StockReserved(UUID eventId, Instant occurredAt, long orderId) implements DomainEvent {
}
