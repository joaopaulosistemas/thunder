package com.example.shop.orders;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Metrica "shop.orders.pending": pedidos aguardando a saga. Crescimento sustentado = saga travada. */
@Component
class OrderMetrics {

    OrderMetrics(MeterRegistry registry, OrderRepository orders) {
        Gauge.builder("shop.orders.pending", orders, o -> o.countByStatus(OrderStatus.PENDING))
                .description("Pedidos PENDING aguardando a reserva de estoque")
                .register(registry);
    }
}
