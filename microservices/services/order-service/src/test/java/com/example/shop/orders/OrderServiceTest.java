package com.example.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.DomainEvent;
import com.example.shop.contracts.OrderCreated;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.Topics;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductSnapshotRepository snapshots = mock(ProductSnapshotRepository.class);
    private final Outbox outbox = mock(Outbox.class);
    private final OrderService service = new OrderService(orders, snapshots, outbox);

    @Test
    void criaPedidoPendenteComTotalEPublicaOrderCreated() {
        when(snapshots.findById(5L)).thenReturn(Optional.of(new ProductSnapshot(5L, "Teclado", 1500)));
        when(orders.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 42L);
            return o;
        });

        Order order = service.place("alice", 5L, 3);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalCents()).isEqualTo(4500L);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.ORDER_CREATED), eq("42"), sent.capture());
        OrderCreated event = (OrderCreated) sent.getValue();
        assertThat(event.orderId()).isEqualTo(42L);
        assertThat(event.customer()).isEqualTo("alice");
    }

    @Test
    void produtoDesconhecidoNaoCriaPedidoNemEvento() {
        when(snapshots.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place("alice", 9L, 1)).isInstanceOf(UnknownProduct.class);

        verify(orders, never()).save(any());
        verify(outbox, never()).append(any(), any(), any());
    }

    private static Order orderWithId(String customer) {
        Order o = new Order(customer, 5L, 2, 3000L);
        ReflectionTestUtils.setField(o, "id", 42L);
        return o;
    }

    @Test
    void cancelarPedidoConfirmadoPedeADevolucaoDoEstoque() {
        Order order = orderWithId("alice");
        order.confirm();
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        service.cancel(42L, "alice", false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox).append(eq(Topics.ORDER_CANCELLED), eq("42"), any(DomainEvent.class));
        verify(outbox).append(eq(Topics.STOCK_RELEASE), eq("42"), any(StockReleaseRequested.class));
    }

    @Test
    void cancelarPedidoPendenteNaoDevolveAindaPoisAReservaPodeChegarDepois() {
        Order order = orderWithId("alice");
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        service.cancel(42L, "alice", false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox).append(eq(Topics.ORDER_CANCELLED), eq("42"), any(DomainEvent.class));
        verify(outbox, never()).append(eq(Topics.STOCK_RELEASE), any(), any());
    }

    @Test
    void pedidoDeOutroClienteSeComportaComoInexistente() {
        when(orders.findById(42L)).thenReturn(Optional.of(orderWithId("alice")));

        assertThatThrownBy(() -> service.cancel(42L, "bob", false)).isInstanceOf(OrderNotFound.class);
    }

    @Test
    void adminPodeCancelarPedidoDeQualquerCliente() {
        Order order = orderWithId("alice");
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        service.cancel(42L, "bob", true);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void naoCancelaPedidoJaRecusadoOuCancelado() {
        Order rejected = orderWithId("alice");
        rejected.reject();
        when(orders.findById(42L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.cancel(42L, "alice", false)).isInstanceOf(OrderNotCancellable.class);
        verify(outbox, never()).append(any(), any(), any());
    }
}
