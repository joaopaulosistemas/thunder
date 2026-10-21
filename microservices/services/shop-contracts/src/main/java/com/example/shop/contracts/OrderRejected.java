package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** Pedido recusado: o cliente deve ser avisado. */
public record OrderRejected(UUID eventId, Instant occurredAt, long orderId, String customer, String reason)
        implements DomainEvent {
}
