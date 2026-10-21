package com.example.shop.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Pega os pendentes mais antigos e TRAVA as linhas; "skip locked" faz cada replica do servico
     * pegar um lote diferente, sem esperar nem publicar o mesmo evento duas vezes ao mesmo tempo.
     */
    @Query(value = "select * from outbox_event where published_at is null "
            + "order by created_at, id limit :limit for update skip locked", nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("limit") int limit);

    /**
     * Lock de aviso do Postgres valido ate o fim da transacao: so UMA replica publica por vez. Sem isso,
     * duas replicas pegariam lotes diferentes e eventos da mesma chave poderiam chegar ao Kafka fora de ordem.
     */
    @Query(value = "select pg_try_advisory_xact_lock(727001)", nativeQuery = true)
    boolean tryBecomePublisher();

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

    /** Usa o indice parcial de pendentes (idx_outbox_pending). */
    long countByPublishedAtIsNull();
}
