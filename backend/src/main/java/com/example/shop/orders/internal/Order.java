package com.example.shop.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(nullable = false)
    private String customer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Order() {
    }

    Order(Long productId, int quantity, long totalCents, String customer) {
        this.productId = productId;
        this.quantity = quantity;
        this.totalCents = totalCents;
        this.customer = customer;
        this.createdAt = Instant.now();
    }

    Long getId() {
        return id;
    }

    Long getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }

    long getTotalCents() {
        return totalCents;
    }

    String getCustomer() {
        return customer;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
