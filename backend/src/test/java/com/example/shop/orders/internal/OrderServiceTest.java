package com.example.shop.orders.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shop.catalog.api.CatalogApi;
import com.example.shop.catalog.api.ProductView;
import com.example.shop.config.FeatureFlags;
import com.example.shop.orders.api.OrderCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Teste unitario puro (base da piramide): sem Spring, sem banco. */
class OrderServiceTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final CatalogApi catalog = mock(CatalogApi.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void setUp() {
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(catalog.findProduct(1L)).thenReturn(Optional.of(new ProductView(1L, "BK-001", "Livro", 1000)));
    }

    private OrderService service(boolean bulkDiscount) {
        return new OrderService(repository, catalog, new FeatureFlags(bulkDiscount), events, new SimpleMeterRegistry());
    }

    @Test
    void appliesBulkDiscountOnlyWhenFlagIsOnAndQuantityReachesThreshold() {
        assertThat(service(true).create(1L, 10, "alice").totalCents()).isEqualTo(9000);
        assertThat(service(true).create(1L, 9, "alice").totalCents()).isEqualTo(9000);
        assertThat(service(false).create(1L, 10, "alice").totalCents()).isEqualTo(10000);
    }

    @Test
    void publishesDomainEventWithTheCustomerAfterSaving() {
        service(false).create(1L, 2, "alice");

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().customer()).isEqualTo("alice");
    }

    @Test
    void failsForUnknownProduct() {
        assertThatThrownBy(() -> service(false).create(99L, 1, "alice"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void regularUserQueriesOnlyOwnOrdersAndAdminQueriesAll() {
        service(false).latest("alice", false);
        verify(repository).findTop20ByCustomerOrderByIdDesc("alice");

        service(false).latest("bob", true);
        verify(repository).findTop20ByOrderByIdDesc();
    }
}
