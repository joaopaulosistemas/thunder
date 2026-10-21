package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** Pedido cancelado pelo cliente (ou por um administrador): o cliente deve ser avisado. */
public record OrderCancelled(UUID eventId, Instant occurredAt, long orderId, String customer, String reason)
        implements DomainEvent {
}
