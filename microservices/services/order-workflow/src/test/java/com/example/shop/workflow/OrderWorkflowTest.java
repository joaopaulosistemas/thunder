package com.example.shop.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Usa o servidor de teste em memoria do Temporal (sem Docker). */
class OrderWorkflowTest {

    private static final String QUEUE = "order-test";
    private static final OrderRequest REQUEST = new OrderRequest(7L, "alice", 1L, 2);

    private TestWorkflowEnvironment env;
    private OrderActivities activities;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(QUEUE);
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        activities = mock(OrderActivities.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private OrderWorkflow workflow() {
        return client.newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(QUEUE).build());
    }

    @Test
    void confirmaQuandoHaEstoque() {
        when(activities.reserveStock(any())).thenReturn(true);

        assertThat(workflow().placeOrder(REQUEST)).isEqualTo("CONFIRMED");

        verify(activities).confirmOrder(7L);
        verify(activities, never()).releaseStock(any());
    }

    @Test
    void recusaSemEstoqueENaoDevolveNada() {
        when(activities.reserveStock(any())).thenReturn(false);

        assertThat(workflow().placeOrder(REQUEST)).isEqualTo("REJECTED");

        verify(activities).rejectOrder(7L, "estoque insuficiente");
        verify(activities, never()).releaseStock(any());
    }

    @Test
    void devolveOEstoqueQuandoConfirmarFalhaSempre() {
        when(activities.reserveStock(any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("pedido fora do ar"))
                .when(activities).confirmOrder(7L);

        assertThat(workflow().placeOrder(REQUEST)).isEqualTo("REJECTED");

        verify(activities).releaseStock(REQUEST);
        verify(activities).rejectOrder(7L, "falha no processamento");
    }
}
