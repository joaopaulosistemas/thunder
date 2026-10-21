package com.example.shop.common.inbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotencia dos consumidores. Chamado DENTRO da transacao que trata o evento: se o evento ja foi
 * visto devolve false (o chamador ignora); se dois processos disputam o mesmo evento, o segundo
 * falha na chave primaria, a transacao desfaz e a reentrega cai no caso "ja visto".
 */
@Service
public class Inbox {

    private final ProcessedEventRepository repository;

    public Inbox(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, UUID eventId) {
        String id = consumer + ":" + eventId;
        if (repository.existsById(id)) {
            return false;
        }
        repository.saveAndFlush(new ProcessedEvent(id, Instant.now()));
        return true;
    }
}
