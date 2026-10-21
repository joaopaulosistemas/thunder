package com.example.shop.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Tambem e usado quando o pedido e de outro cliente: nao revela que ele existe. */
@ResponseStatus(HttpStatus.NOT_FOUND)
class OrderNotFound extends RuntimeException {
    OrderNotFound(long id) {
        super("Pedido " + id + " nao existe");
    }
}
