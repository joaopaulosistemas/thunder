package com.example.shop.catalog;

import com.example.shop.common.inbox.Inbox;
import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.OrderCreated;
import com.example.shop.contracts.StockRejected;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.StockReserved;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passo do catalogo na saga do pedido: reservar estoque. Numa unica transacao: marca o evento como
 * visto (inbox), baixa o estoque com a linha travada e registra a resposta no outbox.
 */
@Service
public class StockService {

    static final String CONSUMER = "catalog.order-created";
    static final String RELEASE_CONSUMER = "catalog.stock-release";

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final Inbox inbox;
    private final Outbox outbox;
    private final ProductCache cache;

    public StockService(ProductRepository products, StockReservationRepository reservations, Inbox inbox,
            Outbox outbox, ProductCache cache) {
        this.products = products;
        this.reservations = reservations;
        this.inbox = inbox;
        this.outbox = outbox;
        this.cache = cache;
    }

    @Transactional
    public void onOrderCreated(OrderCreated order) {
        if (!inbox.firstTime(CONSUMER, order.eventId())) {
            return; // reentrega: ja tratado
        }
        String key = String.valueOf(order.orderId());
        Product product = products.findByIdForUpdate(order.productId()).orElse(null);
        if (product == null) {
            reject(key, order, "produto inexistente");
        } else if (!product.reserve(order.quantity())) {
            reject(key, order, "estoque insuficiente");
        } else {
            reservations.save(new StockReservation(order.orderId(), order.productId(), order.quantity()));
            cache.evictAfterCommit(order.productId()); // o estoque mudou: a copia no cache esta velha
            outbox.append(Topics.STOCK_RESERVED, key, new StockReserved(UUID.randomUUID(), Instant.now(), order.orderId()));
        }
    }

    /**
     * Compensacao: devolve o estoque reservado a um pedido que nao vai acontecer. Idempotente em dois
     * niveis: o inbox ignora o mesmo evento repetido e a reserva so e devolvida uma vez por pedido.
     */
    @Transactional
    public void onStockRelease(StockReleaseRequested event) {
        if (!inbox.firstTime(RELEASE_CONSUMER, event.eventId())) {
            return;
        }
        reservations.findById(event.orderId()).filter(r -> !r.isReleased()).ifPresent(reservation -> {
            products.findByIdForUpdate(reservation.getProductId()).ifPresent(p -> p.release(reservation.getQuantity()));
            cache.evictAfterCommit(reservation.getProductId());
            reservation.markReleased();
        });
    }

    private void reject(String key, OrderCreated order, String reason) {
        outbox.append(Topics.STOCK_REJECTED, key, new StockRejected(UUID.randomUUID(), Instant.now(), order.orderId(), reason));
    }
}
