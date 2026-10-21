package com.example.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shop.common.outbox.OutboxRepository;
import com.example.shop.contracts.StockReserved;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integracao com Postgres e Kafka REAIS (migracoes Flyway, validacao do Hibernate, SQL nativo, transacoes).
 * Os testes unitarios usam mocks e nao pegam erros como transacao ignorada ou SQL invalido.
 * Sem Docker na maquina, e pulado. Os agendadores ficam desligados (intervalos enormes): o teste chama os metodos.
 */
@SpringBootTest(properties = {
        "app.orders.reaper-poll-ms=3600000",
        "app.outbox.poll-ms=3600000",
        "spring.kafka.listener.auto-startup=false"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @Autowired OrderService service;
    @Autowired OrderSaga saga;
    @Autowired PendingOrderReaper reaper;
    @Autowired OrderRepository orders;
    @Autowired ProductSnapshotRepository snapshots;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private long newOrder(String customer) {
        snapshots.save(new ProductSnapshot(1L, "Teclado", 1000));
        return service.place(customer, 1L, 2).getId();
    }

    private List<String> topicsFor(long orderId) {
        return jdbc.queryForList("select topic from outbox_event where event_key = ? order by created_at",
                String.class, String.valueOf(orderId));
    }

    @Test
    void reaperRecusaPedidoVencidoComTransacaoReal() {
        long id = newOrder("reaper-user");
        jdbc.update("update orders set created_at = now() - interval '10 minutes' where id = ?", id);

        reaper.run(); // pelo proxy do Spring: precisa abrir a transacao do "for update skip locked"

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(topicsFor(id)).contains(Topics.ORDER_REJECTED);
    }

    @Test
    void reaperNaoMexeEmPedidoRecente() {
        long id = newOrder("recent-user");

        reaper.run();

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void cancelarPedidoConfirmadoPedeADevolucaoDoEstoque() {
        long id = newOrder("cancel-user");
        saga.onStockReserved(new StockReserved(UUID.randomUUID(), Instant.now(), id));
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        service.cancel(id, "cancel-user", false);

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(topicsFor(id)).contains(Topics.ORDER_CANCELLED, Topics.STOCK_RELEASE);
    }

    @Test
    void reservaTardiaDePedidoCancelado_pedeDevolucao() {
        long id = newOrder("late-user");
        service.cancel(id, "late-user", false);

        saga.onStockReserved(new StockReserved(UUID.randomUUID(), Instant.now(), id));

        assertThat(topicsFor(id)).contains(Topics.STOCK_RELEASE);
    }

    @Test
    void soUmaReplicaPublicaPorVez() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<Boolean> first = pool.submit(() -> tx.execute(status -> {
                boolean got = outbox.tryBecomePublisher();
                holding.countDown();
                await(release);
                return got;
            }));
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            Boolean second = tx.execute(status -> outbox.tryBecomePublisher());

            assertThat(second).isFalse(); // a primeira transacao ainda segura o lock
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(tx.execute(status -> outbox.tryBecomePublisher())).isTrue(); // liberado ao commit
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
