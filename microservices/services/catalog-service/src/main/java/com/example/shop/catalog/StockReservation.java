package com.example.shop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Registro de que o pedido reservou estoque; base da devolucao idempotente. */
@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    @Id
    @Column(name = "order_id")
    private long orderId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean released;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockReservation() {
    }

    public StockReservation(long orderId, long productId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isReleased() {
        return released;
    }

    public void markReleased() {
        this.released = true;
    }
}
