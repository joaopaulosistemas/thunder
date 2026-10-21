package com.example.shop.orders;

import com.example.shop.common.inbox.Inbox;
import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.OrderConfirmed;
import com.example.shop.contracts.OrderRejected;
import com.example.shop.contracts.ProductChanged;
import com.example.shop.contracts.StockRejected;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.StockReserved;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga coreografada do pedido, lado do order-service:
 * orders.created -> (catalogo) -> stock.reserved | stock.rejected -> pedido CONFIRMADO | RECUSADO
 * e avisa o cliente (orders.confirmed | orders.rejected). Nao existe orquestrador central.
 */
@Service
public class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);
    static final String RESERVED = "orders.stock-reserved";
    static final String REJECTED = "orders.stock-rejected";
    static final String PRODUCT = "orders.product-changed";

    private final OrderRepository orders;
    private final ProductSnapshotRepository snapshots;
    private final Inbox inbox;
    private final Outbox outbox;

    public OrderSaga(OrderRepository orders, ProductSnapshotRepository snapshots, Inbox inbox, Outbox outbox) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Transactional
    public void onStockReserved(StockReserved event) {
        if (!inbox.firstTime(RESERVED, event.eventId())) {
            return;
        }
        orders.findById(event.orderId()).filter(Order::isPending).ifPresentOrElse(order -> {
            order.confirm();
            outbox.append(Topics.ORDER_CONFIRMED, String.valueOf(order.getId()),
                    new OrderConfirmed(UUID.randomUUID(), Instant.now(), order.getId(), order.getCustomer(), order.getTotalCents()));
        }, () -> compensateLateReservation(event));
    }

    /**
     * A reserva chegou, mas o pedido nao esta mais PENDING. Se ele foi cancelado ou recusado por
     * tempo, o estoque ficou preso: pede ao catalogo para devolver. Um pedido ja CONFIRMED (evento
     * duplicado) ou inexistente nao gera devolucao.
     */
    private void compensateLateReservation(StockReserved event) {
        orders.findById(event.orderId())
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.REJECTED)
                .ifPresentOrElse(
                        o -> outbox.append(Topics.STOCK_RELEASE, String.valueOf(o.getId()),
                                new StockReleaseRequested(UUID.randomUUID(), Instant.now(), o.getId(),
                                        o.getProductId(), o.getQuantity(), "reserva tardia: pedido " + o.getStatus())),
                        () -> log.warn("stock.reserved sem pedido a compensar: {}", event.orderId()));
    }

    @Transactional
    public void onStockRejected(StockRejected event) {
        if (!inbox.firstTime(REJECTED, event.eventId())) {
            return;
        }
        orders.findById(event.orderId()).filter(Order::isPending).ifPresentOrElse(order -> {
            order.reject();
            outbox.append(Topics.ORDER_REJECTED, String.valueOf(order.getId()),
                    new OrderRejected(UUID.randomUUID(), Instant.now(), order.getId(), order.getCustomer(), event.reason()));
        }, () -> log.warn("stock.rejected para pedido inexistente ou ja decidido: {}", event.orderId()));
    }

    /**
     * Mantem a copia local do preco. A ordem por produto vem do Kafka (mesma chave, mesma particao);
     * repetir um evento nao muda o resultado (salvar ou apagar de novo), por isso nao precisa do inbox.
     * Um UPSERTED com versao menor que a da copia local e ignorado. Removido: o produto sai da copia local e pedidos novos dele passam a ser recusados.
     */
    @Transactional
    public void onProductChanged(ProductChanged event) {
        if (event.type() == ProductChanged.Type.REMOVED) {
            snapshots.deleteById(event.productId());
        } else if (snapshots.findById(event.productId()).filter(s -> s.getCatalogVersion() > event.version()).isPresent()) {
            log.info("UPSERTED antigo ignorado para o produto {} (versao {})", event.productId(), event.version());
        } else {
            snapshots.save(new ProductSnapshot(event.productId(), event.name(), event.priceCents(), event.version()));
        }
    }
}
