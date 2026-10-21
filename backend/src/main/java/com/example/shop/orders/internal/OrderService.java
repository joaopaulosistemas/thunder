package com.example.shop.orders.internal;

import com.example.shop.catalog.api.CatalogApi;
import com.example.shop.catalog.api.ProductView;
import com.example.shop.config.FeatureFlags;
import com.example.shop.orders.api.OrderCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {

    private static final int BULK_THRESHOLD = 10;
    private static final int BULK_DISCOUNT_PERCENT = 10;

    private final OrderRepository repository;
    private final CatalogApi catalog;
    private final FeatureFlags flags;
    private final ApplicationEventPublisher events;
    private final Counter created;

    OrderService(OrderRepository repository, CatalogApi catalog, FeatureFlags flags,
                 ApplicationEventPublisher events, MeterRegistry registry) {
        this.repository = repository;
        this.catalog = catalog;
        this.flags = flags;
        this.events = events;
        this.created = Counter.builder("shop.orders.created").description("Pedidos criados").register(registry);
    }

    @Transactional
    OrderResponse create(Long productId, int quantity, String customer) {
        ProductView product = catalog.findProduct(productId)
                .orElseThrow(() -> new NoSuchElementException("Produto " + productId + " nao existe"));
        long total = (long) product.priceCents() * quantity;
        if (flags.bulkDiscount() && quantity >= BULK_THRESHOLD) {
            total = total * (100 - BULK_DISCOUNT_PERCENT) / 100;
        }
        Order saved = repository.save(new Order(productId, quantity, total, customer));
        created.increment();
        events.publishEvent(new OrderCreatedEvent(saved.getId(), saved.getProductId(), saved.getQuantity(),
                saved.getTotalCents(), saved.getCustomer(), saved.getCreatedAt()));
        return OrderResponse.from(saved);
    }

    /** Admin ve os ultimos pedidos de todos; usuario comum ve so os proprios. */
    @Transactional(readOnly = true)
    List<OrderResponse> latest(String customer, boolean all) {
        List<Order> orders = all
                ? repository.findTop20ByOrderByIdDesc()
                : repository.findTop20ByCustomerOrderByIdDesc(customer);
        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    AdminSummary summary() {
        return new AdminSummary(repository.count(), repository.totalRevenueCents());
    }

    record AdminSummary(long orders, long revenueCents) {
    }
}
