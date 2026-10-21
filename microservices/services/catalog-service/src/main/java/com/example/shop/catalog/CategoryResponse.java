package com.example.shop.catalog;

public record CategoryResponse(long id, String name, long version) {

    static CategoryResponse of(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getVersion());
    }
}
