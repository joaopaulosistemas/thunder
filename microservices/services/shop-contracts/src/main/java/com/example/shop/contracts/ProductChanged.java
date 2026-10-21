package com.example.shop.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * O catalogo anuncia que um produto mudou: foi criado/alterado (UPSERTED) ou removido (REMOVED).
 *
 * <p>Um unico topico, com o id do produto como chave, garante a ordem: o Kafka mantem na mesma
 * particao, e em sequencia, todos os eventos de um mesmo produto. Com dois topicos (um para upsert,
 * outro para remocao) um upsert atrasado poderia recriar um produto ja removido.
 *
 * <p>{@code version} e a versao (bloqueio otimista) do produto no catalogo. Quem consome ignora um
 * UPSERTED mais velho que o que ja tem (reprocessamento da DLT, reentrega fora de ordem). Mensagens antigas,
 * sem o campo, chegam com versao 0.
 *
 * <p>Em REMOVED, {@code name}, {@code priceCents} e {@code version} nao sao usados (vao vazios/zero).
 */
public record ProductChanged(UUID eventId, Instant occurredAt, long productId, Type type, String name, int priceCents,
        long version) implements DomainEvent {

    public enum Type {
        UPSERTED, REMOVED
    }

    public static ProductChanged upserted(long productId, String name, int priceCents, long version) {
        return new ProductChanged(UUID.randomUUID(), Instant.now(), productId, Type.UPSERTED, name, priceCents,
                version);
    }

    public static ProductChanged removed(long productId) {
        return new ProductChanged(UUID.randomUUID(), Instant.now(), productId, Type.REMOVED, "", 0, 0);
    }
}
