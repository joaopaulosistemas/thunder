package com.example.shop.workflow;

import io.temporal.activity.ActivityInterface;

/**
 * Passos com efeito no mundo. Cada um precisa ser IDEMPOTENTE: o Temporal repete a chamada se o worker cair.
 * A implementacao real chamaria order-service e catalog-service (HTTP ou request/reply Kafka).
 */
@ActivityInterface
public interface OrderActivities {

    /** Devolve true se reservou; false se nao ha estoque. */
    boolean reserveStock(OrderRequest request);

    /** Compensacao de reserveStock. Idempotente: devolver duas vezes o mesmo pedido nao muda nada. */
    void releaseStock(OrderRequest request);

    void confirmOrder(long orderId);

    void rejectOrder(long orderId, String reason);

    void cancelOrder(long orderId);
}
