package com.example.shop.common.outbox;

import com.example.shop.common.inbox.ProcessedEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retencao: sem isto, outbox_event e processed_event crescem para sempre. O inbox precisa guardar mais
 * tempo que a retencao do Kafka (7 dias por padrao), senao uma reentrega tardia seria tratada de novo.
 */
@Component
class OutboxCleaner {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleaner.class);

    private final OutboxRepository outbox;
    private final ProcessedEventRepository inbox;
    private final Duration outboxRetention;
    private final Duration inboxRetention;

    OutboxCleaner(OutboxRepository outbox, ProcessedEventRepository inbox,
            @Value("${app.outbox.retention:P3D}") Duration outboxRetention,
            @Value("${app.inbox.retention:P14D}") Duration inboxRetention) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.outboxRetention = outboxRetention;
        this.inboxRetention = inboxRetention;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 17 3 * * *}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        int events = outbox.deletePublishedBefore(now.minus(outboxRetention));
        int seen = inbox.deleteProcessedBefore(now.minus(inboxRetention));
        if (events > 0 || seen > 0) {
            log.info("Limpeza: {} eventos do outbox e {} registros do inbox removidos", events, seen);
        }
    }
}
