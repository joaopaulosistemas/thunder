package com.example.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shop.contracts.OrderCreated;
import com.example.shop.contracts.StockReleaseRequested;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Integracao com Postgres e Kafka reais: busca paginada (Specification) e reserva/devolucao de estoque. */
@SpringBootTest(properties = {
        "app.outbox.poll-ms=3600000",
        "spring.kafka.listener.auto-startup=false",
        "management.health.redis.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class CatalogFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @Autowired CatalogService catalog;
    @Autowired StockService stock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void buscaPorNomeFiltraNoBanco() {
        var result = catalog.search("livro", null, 0, 10, "name", false);

        assertThat(result.total()).isEqualTo(2); // os dois livros do seed (V2__catalog.sql)
        assertThat(result.items()).extracting(ProductResponse::name).allMatch(n -> n.toLowerCase().contains("livro"));
    }

    @Test
    void curingaDigitadoPeloUsuarioNaoViraCuringaDoLike() {
        assertThat(catalog.search("%", null, 0, 10, "name", false).total()).isZero();
    }

    @Test
    void paginacaoRespeitaOTamanhoEOTotal() {
        var page = catalog.search(null, null, 0, 1, "name", false);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void estoqueEDevolvidoUmaUnicaVezPorPedido() {
        long productId = jdbc.queryForObject("select id from products where sku = 'KB-001'", Long.class);
        int before = jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId);
        long orderId = 9_000_000L + (System.nanoTime() % 1000);

        stock.onOrderCreated(new OrderCreated(UUID.randomUUID(), Instant.now(), orderId, productId, 3, "alice"));
        assertThat(jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId))
                .isEqualTo(before - 3);

        var release1 = new StockReleaseRequested(UUID.randomUUID(), Instant.now(), orderId, productId, 3, "teste");
        var release2 = new StockReleaseRequested(UUID.randomUUID(), Instant.now(), orderId, productId, 3, "teste");
        stock.onStockRelease(release1);
        stock.onStockRelease(release2); // evento diferente para o mesmo pedido: nao pode devolver de novo

        assertThat(jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId))
                .isEqualTo(before);
    }
}
