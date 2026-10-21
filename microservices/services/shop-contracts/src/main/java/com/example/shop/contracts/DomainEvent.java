package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/** Todo evento tem um id unico (base da idempotencia dos consumidores) e o instante do fato. */
public interface DomainEvent {
    UUID eventId();

    Instant occurredAt();
}
