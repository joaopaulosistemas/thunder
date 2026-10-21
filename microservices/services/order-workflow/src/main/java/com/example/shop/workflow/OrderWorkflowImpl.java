package com.example.shop.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Mesma saga do ADR 0006 em forma de codigo sequencial. O que antes eram o reaper (timeout), a tabela
 * stock_reservation (idempotencia) e a "reserva tardia" vira: timeout da activity, retentativa e Saga.
 * ATENCAO: codigo de workflow deve ser deterministico (sem relogio, sem aleatorio, sem I/O direto).
 */
public class OrderWorkflowImpl implements OrderWorkflow {

    private final OrderActivities activities = Workflow.newActivityStub(OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .build())
                    .build());

    private boolean cancelRequested;

    @Override
    public String placeOrder(OrderRequest request) {
        Saga saga = new Saga(new Saga.Options.Builder().setParallelCompensation(false).build());
        try {
            boolean reserved = activities.reserveStock(request);
            if (!reserved) {
                activities.rejectOrder(request.orderId(), "estoque insuficiente");
                return "REJECTED";
            }
            saga.addCompensation(activities::releaseStock, request);

            if (cancelRequested) {
                throw new CancelledByCustomer();
            }
            activities.confirmOrder(request.orderId());
            return "CONFIRMED";
        } catch (CancelledByCustomer e) {
            saga.compensate();
            activities.cancelOrder(request.orderId());
            return "CANCELLED";
        } catch (RuntimeException e) {
            // Falha depois das tentativas: desfaz o que foi feito e recusa o pedido.
            saga.compensate();
            activities.rejectOrder(request.orderId(), "falha no processamento");
            return "REJECTED";
        }
    }

    @Override
    public void cancel() {
        cancelRequested = true;
    }

    /** Interna ao fluxo; nao atravessa a rede. */
    private static final class CancelledByCustomer extends RuntimeException {
    }
}
