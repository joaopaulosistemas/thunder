package com.example.shop.orders.api;

import java.time.Instant;

/**
 * Evento de dominio publicado quando um pedido e criado. E o contrato publico
 * do modulo orders para quem quiser reagir (dentro do monolito ou via Kafka).
 */
public record OrderCreatedEvent(Long orderId, Long productId, int quantity, long totalCents,
                                String customer, Instant createdAt) {
}
