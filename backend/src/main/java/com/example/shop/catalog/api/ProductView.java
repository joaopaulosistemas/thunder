package com.example.shop.catalog.api;

import java.io.Serializable;

public record ProductView(Long id, String sku, String name, int priceCents) implements Serializable {
}
