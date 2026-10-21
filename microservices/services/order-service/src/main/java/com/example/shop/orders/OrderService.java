package com.example.shop.orders;

import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.OrderCancelled;
import com.example.shop.contracts.OrderCreated;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductSnapshotRepository snapshots;
    private final Outbox outbox;

    public OrderService(OrderRepository orders, ProductSnapshotRepository snapshots, Outbox outbox) {
        this.orders = orders;
        this.snapshots = snapshots;
        this.outbox = outbox;
    }

    /**
     * Cria o pedido PENDENTE e registra orders.created na mesma transacao. A confirmacao
     * vem depois, de forma assincrona, pela saga (estoque reservado ou recusado).
     */
    @Transactional
    public Order place(String customer, long productId, int quantity) {
        ProductSnapshot product = snapshots.findById(productId).orElseThrow(() -> new UnknownProduct(productId));
        long total = (long) product.getPriceCents() * quantity;
        Order order = orders.save(new Order(customer, productId, quantity, total));
        outbox.append(Topics.ORDER_CREATED, String.valueOf(order.getId()),
                new OrderCreated(UUID.randomUUID(), Instant.now(), order.getId(), productId, quantity, customer));
        return order;
    }

    /**
     * Cancela um pedido PENDING ou CONFIRMED. Se ja estava CONFIRMED o estoque esta reservado, entao
     * pede a devolucao. Se estava PENDING a reserva pode chegar depois: a saga compensa nessa hora.
     * Pedido de outro cliente se comporta como inexistente (404), exceto para admin.
     */
    @Transactional
    public Order cancel(long id, String customer, boolean admin) {
        Order order = orders.findById(id)
                .filter(o -> admin || o.getCustomer().equals(customer))
                .orElseThrow(() -> new OrderNotFound(id));
        if (!order.isCancellable()) {
            throw new OrderNotCancellable(id, order.getStatus());
        }
        boolean stockHeld = order.isConfirmed();
        order.cancel();
        String key = String.valueOf(order.getId());
        outbox.append(Topics.ORDER_CANCELLED, key,
                new OrderCancelled(UUID.randomUUID(), Instant.now(), order.getId(), order.getCustomer(), "cancelado a pedido"));
        if (stockHeld) {
            outbox.append(Topics.STOCK_RELEASE, key, new StockReleaseRequested(UUID.randomUUID(), Instant.now(),
                    order.getId(), order.getProductId(), order.getQuantity(), "pedido cancelado"));
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> recent(String customer, boolean admin) {
        return admin ? orders.findTop20ByOrderByIdDesc() : orders.findTop20ByCustomerOrderByIdDesc(customer);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(orders.count(), orders.countByStatus(OrderStatus.CONFIRMED), orders.confirmedRevenueCents());
    }

    public record Summary(long orders, long confirmed, long totalRevenueCents) {
    }
}
