package com.example.shop.common.outbox;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relay do outbox: le os eventos pendentes e os envia ao Kafka, marcando-os como publicados.
 * Se o Kafka estiver fora, os eventos ficam no banco e saem quando ele voltar (nada se perde).
 * Pode haver reenvio se o processo cair entre o envio e o commit: por isso os consumidores sao idempotentes.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:500}")
    @Transactional
    public void publishPending() {
        if (!repository.tryBecomePublisher()) {
            return; // outra replica esta publicando; a ordem por chave depende de haver um so
        }
        for (OutboxEvent event : repository.lockPending(batchSize)) {
            try {
                kafka.send(event.getTopic(), event.getKey(), event.getPayload()).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Para no primeiro erro para preservar a ordem; tenta de novo no proximo ciclo.
                log.warn("Falha ao publicar o evento {} no topico {}: {}", event.getId(), event.getTopic(), e.toString());
                return;
            }
        }
    }
}
