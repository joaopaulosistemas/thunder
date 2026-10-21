package com.example.shop.orders;

public enum OrderStatus {
    /** Criado; aguardando o catalogo reservar o estoque. */
    PENDING,
    CONFIRMED,
    REJECTED,
    /** Cancelado pelo cliente (ou admin) antes ou depois da confirmacao. */
    CANCELLED
}
