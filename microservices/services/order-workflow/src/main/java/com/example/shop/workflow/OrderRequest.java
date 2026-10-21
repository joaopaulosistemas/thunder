package com.example.shop.workflow;

/** Entrada do fluxo. Serializada em JSON pelo Temporal, por isso e um record simples. */
public record OrderRequest(long orderId, String customer, long productId, int quantity) {
}
