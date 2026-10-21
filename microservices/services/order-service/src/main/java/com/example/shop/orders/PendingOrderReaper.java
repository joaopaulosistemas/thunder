package com.example.shop.orders;

import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.OrderRejected;
import com.example.shop.contracts.Topics;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Timeout da saga: um pedido que fica PENDING alem do limite (evento perdido, catalogo fora do ar)
 * e recusado e o cliente e avisado, em vez de ficar pendente para sempre. Se o estoque for reservado
 * DEPOIS disso, a saga pede a devolucao (ver OrderSaga.onStockReserved).
 */
@Component
class PendingOrderReaper {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderReaper.class);
    static final String REASON = "tempo esgotado sem resposta do estoque";

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Duration timeout;
    private final int batchSize;

    PendingOrderReaper(OrderRepository orders, Outbox outbox,
            @Value("${app.orders.pending-timeout:PT2M}") Duration timeout,
            @Value("${app.orders.reaper-batch:100}") int batchSize) {
        this.orders = orders;
        this.outbox = outbox;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }

    /**
     * A transacao fica AQUI e nao em expirePending: run() chama expirePending por "this", o que
     * contornaria o proxy do Spring e executaria o "for update" sem transacao.
     */
    @Scheduled(fixedDelayString = "${app.orders.reaper-poll-ms:30000}")
    @Transactional
    public void run() {
        int rejected = expirePending(Instant.now());
        if (rejected > 0) {
            log.warn("{} pedido(s) PENDING recusado(s) por tempo esgotado", rejected);
        }
    }

    /** Deve rodar dentro de uma transacao (o lock "for update" vale ate o commit). */
    int expirePending(Instant now) {
        var expired = orders.lockExpiredPending(now.minus(timeout), batchSize);
        for (Order order : expired) {
            order.reject();
            outbox.append(Topics.ORDER_REJECTED, String.valueOf(order.getId()),
                    new OrderRejected(UUID.randomUUID(), now, order.getId(), order.getCustomer(), REASON));
        }
        return expired.size();
    }
}
