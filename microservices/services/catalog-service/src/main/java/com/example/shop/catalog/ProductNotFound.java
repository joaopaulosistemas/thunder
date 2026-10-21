package com.example.shop.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class ProductNotFound extends RuntimeException {
    ProductNotFound(long id) {
        super("Registro " + id + " nao existe");
    }
}
