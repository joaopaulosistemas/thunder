package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** Pedido confirmado: o cliente deve ser avisado. */
public record OrderConfirmed(UUID eventId, Instant occurredAt, long orderId, String customer, long totalCents)
        implements DomainEvent {
}
