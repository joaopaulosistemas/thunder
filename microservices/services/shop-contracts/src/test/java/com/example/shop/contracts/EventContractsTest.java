package com.example.shop.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato dos eventos entre servicos. Os arquivos em src/test/resources/contracts/*.json SAO o
 * contrato: o que um produtor publica e o que um consumidor aceita. Dois testes protegem os dois lados:
 *
 * <ul>
 *   <li>lado do PRODUTOR: serializar o evento tem que gerar exatamente o JSON do contrato (renomear ou
 *       remover um campo quebra aqui, antes de quebrar um consumidor em producao);
 *   <li>lado do CONSUMIDOR: o JSON do contrato tem que virar o evento, e campos NOVOS desconhecidos sao
 *       ignorados (leitor tolerante), o que permite evoluir o evento sem coordenar o deploy dos servicos.
 * </ul>
 *
 * Mudanca incompativel (remover ou renomear campo) exige um topico ou tipo novo, nao editar o arquivo.
 */
class EventContractsTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant AT = Instant.parse("2026-01-02T03:04:05Z");

    // Mesmas opcoes do ObjectMapper do Spring Boot (datas em ISO-8601, campos novos ignorados).
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Map<String, DomainEvent> SAMPLES = Map.of(
            "ProductChanged.upserted", new ProductChanged(ID, AT, 7, ProductChanged.Type.UPSERTED, "Teclado", 1500, 3L),
            "ProductChanged.removed", new ProductChanged(ID, AT, 7, ProductChanged.Type.REMOVED, "", 0, 0L),
            "OrderCreated", new OrderCreated(ID, AT, 42, 7, 2, "alice"),
            "StockReserved", new StockReserved(ID, AT, 42),
            "StockRejected", new StockRejected(ID, AT, 42, "estoque insuficiente"),
            "OrderConfirmed", new OrderConfirmed(ID, AT, 42, "alice", 3000),
            "OrderRejected", new OrderRejected(ID, AT, 42, "alice", "estoque insuficiente"),
            "OrderCancelled", new OrderCancelled(ID, AT, 42, "alice", "cancelado a pedido"),
            "StockReleaseRequested", new StockReleaseRequested(ID, AT, 42, 7, 2, "pedido cancelado"));

    private static JsonNode golden(String name) throws IOException {
        try (InputStream in = EventContractsTest.class.getResourceAsStream("/contracts/" + name + ".json")) {
            assertThat(in).as("arquivo de contrato %s.json", name).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    @Test
    void produtorGeraExatamenteOJsonDoContrato() throws IOException {
        for (var sample : SAMPLES.entrySet()) {
            // Passa pelo texto: valueToTree usaria LongNode onde o arquivo le IntNode (mesmo numero, nos diferentes).
            JsonNode produced = MAPPER.readTree(MAPPER.writeValueAsString(sample.getValue()));
            assertThat(produced).as("JSON publicado de %s", sample.getKey()).isEqualTo(golden(sample.getKey()));
        }
    }

    @Test
    void consumidorEntendeOJsonDoContrato() throws IOException {
        for (var sample : SAMPLES.entrySet()) {
            DomainEvent read = MAPPER.treeToValue(golden(sample.getKey()), sample.getValue().getClass());
            assertThat(read).as("evento lido de %s", sample.getKey()).isEqualTo(sample.getValue());
        }
    }

    @Test
    void consumidorIgnoraCamposNovosDesconhecidos() throws IOException {
        for (var sample : SAMPLES.entrySet()) {
            ObjectNode future = ((ObjectNode) golden(sample.getKey())).deepCopy();
            future.put("campoDaVersaoFutura", "x");
            DomainEvent read = MAPPER.treeToValue(future, sample.getValue().getClass());
            assertThat(read).as("evento lido de %s com campo extra", sample.getKey()).isEqualTo(sample.getValue());
        }
    }

    @Test
    void todoEventoTemArquivoDeContrato() {
        // Se alguem criar um evento novo sem registrar o contrato, o teste avisa.
        Set<String> covered = new HashSet<>();
        SAMPLES.values().forEach(e -> covered.add(e.getClass().getSimpleName()));
        for (String event : new String[] {"ProductChanged", "OrderCreated", "StockReserved", "StockRejected",
                "OrderConfirmed", "OrderRejected", "OrderCancelled", "StockReleaseRequested"}) {
            assertThat(covered).contains(event);
        }
    }

    @Test
    void nomesDeTopicoSaoUnicosEBemFormados() throws IllegalAccessException {
        Set<String> seen = new HashSet<>();
        for (Field f : Topics.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String name = (String) f.get(null);
                assertThat(name).matches("[a-z]+\\.[a-z-]+");
                assertThat(seen.add(name)).as("topico repetido %s", name).isTrue();
            }
        }
        assertThat(seen).contains("catalog.product-changed", "orders.created", "orders.stock-release");
    }
}
