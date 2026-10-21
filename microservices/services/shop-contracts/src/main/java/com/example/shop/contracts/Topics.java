package com.example.shop.contracts;

/** Topicos Kafka. Cada nome pertence a UM servico produtor; os demais so consomem. */
public final class Topics {
    /** Produtor: catalog-service. Chave = id do produto (a ordem por produto vem do Kafka). */
    public static final String PRODUCT_CHANGED = "catalog.product-changed";
    /** Produtor: order-service. */
    public static final String ORDER_CREATED = "orders.created";
    /** Produtor: catalog-service (reservou o estoque). */
    public static final String STOCK_RESERVED = "stock.reserved";
    /** Produtor: catalog-service (sem estoque ou produto inexistente). */
    public static final String STOCK_REJECTED = "stock.rejected";
    /** Produtor: order-service. */
    public static final String ORDER_CONFIRMED = "orders.confirmed";
    /** Produtor: order-service. */
    public static final String ORDER_REJECTED = "orders.rejected";
    /** Produtor: order-service (cancelamento pelo cliente; avisa o cliente). */
    public static final String ORDER_CANCELLED = "orders.cancelled";
    /** Produtor: order-service (compensacao: o catalogo devolve o estoque reservado). */
    public static final String STOCK_RELEASE = "orders.stock-release";

    private Topics() {
    }
}
