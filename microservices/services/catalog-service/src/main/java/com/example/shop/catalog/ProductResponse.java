package com.example.shop.catalog;

public record ProductResponse(long id, String name, int priceCents, int stock, Long categoryId, String categoryName,
        long version) {

    static ProductResponse of(Product p) {
        Category c = p.getCategory();
        return new ProductResponse(p.getId(), p.getName(), p.getPriceCents(), p.getStock(),
                c == null ? null : c.getId(), c == null ? null : c.getName(), p.getVersion());
    }
}
