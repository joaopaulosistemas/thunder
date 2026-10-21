package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** O catalogo nao conseguiu reservar (sem estoque ou produto inexistente). */
public record StockRejected(UUID eventId, Instant occurredAt, long orderId, String reason) implements DomainEvent {
}
