package com.example.shop.common.outbox;

import com.example.shop.contracts.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra um evento para ser publicado. Exige uma transacao ativa: o evento nasce junto do dado. */
@Service
public class Outbox {

    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    public Outbox(OutboxRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, DomainEvent event) {
        try {
            repository.save(new OutboxEvent(event.eventId(), topic, key, mapper.writeValueAsString(event), Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nao foi possivel serializar o evento " + event.eventId(), e);
        }
    }
}
