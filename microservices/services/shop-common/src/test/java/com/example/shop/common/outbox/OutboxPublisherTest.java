package com.example.shop.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxPublisher publisher = new OutboxPublisher(repository, kafka, 50, 1000);

    {
        when(repository.tryBecomePublisher()).thenReturn(true);
    }

    private static OutboxEvent event(String topic) {
        return new OutboxEvent(UUID.randomUUID(), topic, "k", "{}", Instant.now());
    }

    @Test
    void publicaOsPendentesEMarcaComoPublicados() {
        OutboxEvent a = event("t1");
        OutboxEvent b = event("t2");
        when(repository.lockPending(50)).thenReturn(List.of(a, b));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertThat(a.getPublishedAt()).isNotNull();
        assertThat(b.getPublishedAt()).isNotNull();
    }

    @Test
    void quandoOKafkaFalhaMantemOEventoPendenteEPara() {
        OutboxEvent a = event("t1");
        OutboxEvent b = event("t2");
        when(repository.lockPending(50)).thenReturn(List.of(a, b));
        when(kafka.send("t1", "k", "{}")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka fora")));

        publisher.publishPending();

        assertThat(a.getPublishedAt()).isNull();
        assertThat(b.getPublishedAt()).isNull();
        verify(kafka, never()).send("t2", "k", "{}");
    }

    @Test
    void naoPublicaQuandoOutraReplicaJaEstaPublicando() {
        when(repository.tryBecomePublisher()).thenReturn(false);

        publisher.publishPending();

        verify(repository, never()).lockPending(50);
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
