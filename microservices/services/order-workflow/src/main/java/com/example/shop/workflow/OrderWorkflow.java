package com.example.shop.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderWorkflow {

    /** Retorna o estado final: CONFIRMED, REJECTED ou CANCELLED. */
    @WorkflowMethod
    String placeOrder(OrderRequest request);

    /** O cliente cancelou. Vale ate o fluxo terminar a reserva. */
    @SignalMethod
    void cancel();
}
