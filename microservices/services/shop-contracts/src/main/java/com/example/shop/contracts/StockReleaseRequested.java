package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Compensacao da saga: o order-service pede ao catalogo que DEVOLVA o estoque reservado a um pedido
 * que nao vai mais acontecer (cancelado, ou recusado por tempo esgotado e a reserva chegou depois).
 * O catalogo devolve no maximo uma vez por pedido, mesmo que este evento chegue repetido.
 */
public record StockReleaseRequested(UUID eventId, Instant occurredAt, long orderId, long productId, int quantity,
        String reason) implements DomainEvent {
}
