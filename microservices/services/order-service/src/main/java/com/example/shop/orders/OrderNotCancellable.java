package com.example.shop.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
class OrderNotCancellable extends RuntimeException {
    OrderNotCancellable(long id, OrderStatus status) {
        super("Pedido " + id + " esta " + status + " e nao pode ser cancelado");
    }
}
