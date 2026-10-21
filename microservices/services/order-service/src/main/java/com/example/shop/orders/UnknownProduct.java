package com.example.shop.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
class UnknownProduct extends RuntimeException {
    UnknownProduct(long productId) {
        super("Produto " + productId + " desconhecido");
    }
}
