package com.example.shop.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metrica "shop.outbox.pending": quantos eventos estao esperando para ir ao Kafka. Se o numero cresce e
 * nao volta a zero, o Kafka esta fora do ar ou o relay parou (alerta OutboxBacklog).
 */
@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, OutboxRepository repository) {
        Gauge.builder("shop.outbox.pending", repository, OutboxRepository::countByPublishedAtIsNull)
                .description("Eventos do outbox ainda nao publicados no Kafka")
                .register(registry);
    }
}
