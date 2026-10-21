package com.example.shop.catalog;

/** Regra de negocio violada (409): versao desatualizada, nome repetido, categoria em uso. */
class Conflict extends RuntimeException {
    Conflict(String message) {
        super(message);
    }
}
