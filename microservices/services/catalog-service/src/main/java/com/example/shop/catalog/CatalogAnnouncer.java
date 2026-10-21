package com.example.shop.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Na partida o catalogo reanuncia os produtos, para um order-service novo (ou vazio) se alinhar. */
@Component
class CatalogAnnouncer {

    private static final Logger log = LoggerFactory.getLogger(CatalogAnnouncer.class);
    private final CatalogService service;

    CatalogAnnouncer(CatalogService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    void announce() {
        log.info("Anunciando {} produtos", service.announceAll());
    }
}
