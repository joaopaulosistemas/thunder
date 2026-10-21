package com.example.shop.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_snapshot")
public class ProductSnapshot {

    @Id
    @Column(name = "product_id")
    private long productId;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "catalog_version", nullable = false)
    private long catalogVersion;

    protected ProductSnapshot() {
    }

    public ProductSnapshot(long productId, String name, int priceCents) {
        this(productId, name, priceCents, 0);
    }

    public ProductSnapshot(long productId, String name, int priceCents, long catalogVersion) {
        this.productId = productId;
        this.name = name;
        this.priceCents = priceCents;
        this.catalogVersion = catalogVersion;
    }

    public long getCatalogVersion() {
        return catalogVersion;
    }

    public long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getPriceCents() {
        return priceCents;
    }
}
