package com.example.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shop.common.inbox.Inbox;
import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.DomainEvent;
import com.example.shop.contracts.OrderConfirmed;
import com.example.shop.contracts.OrderRejected;
import com.example.shop.contracts.ProductChanged;
import com.example.shop.contracts.StockRejected;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.StockReserved;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrderSagaTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductSnapshotRepository snapshots = mock(ProductSnapshotRepository.class);
    private final Inbox inbox = mock(Inbox.class);
    private final Outbox outbox = mock(Outbox.class);
    private final OrderSaga saga = new OrderSaga(orders, snapshots, inbox, outbox);

    private static Order pending() {
        Order o = new Order("alice", 5L, 2, 3000L);
        ReflectionTestUtils.setField(o, "id", 42L);
        return o;
    }

    @Test
    void estoqueReservadoConfirmaOPedidoEAvisaOCliente() {
        Order order = pending();
        StockReserved event = new StockReserved(UUID.randomUUID(), Instant.now(), 42L);
        when(inbox.firstTime(OrderSaga.RESERVED, event.eventId())).thenReturn(true);
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        saga.onStockReserved(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.ORDER_CONFIRMED), eq("42"), sent.capture());
        assertThat(((OrderConfirmed) sent.getValue()).totalCents()).isEqualTo(3000L);
    }

    @Test
    void estoqueRecusadoRecusaOPedidoComOMotivo() {
        Order order = pending();
        StockRejected event = new StockRejected(UUID.randomUUID(), Instant.now(), 42L, "estoque insuficiente");
        when(inbox.firstTime(OrderSaga.REJECTED, event.eventId())).thenReturn(true);
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        saga.onStockRejected(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.ORDER_REJECTED), eq("42"), sent.capture());
        assertThat(((OrderRejected) sent.getValue()).reason()).isEqualTo("estoque insuficiente");
    }

    @Test
    void eventoRepetidoNaoFazNada() {
        StockReserved event = new StockReserved(UUID.randomUUID(), Instant.now(), 42L);
        when(inbox.firstTime(any(), any())).thenReturn(false);

        saga.onStockReserved(event);

        verify(orders, never()).findById(any());
        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void pedidoJaConfirmadoNaoMudaDeNovoENaoGeraEvento() {
        // Reserva repetida (evento diferente) para um pedido ja CONFIRMADO: nada a fazer.
        // Pedido CANCELADO ou RECUSADO e outro caso: a reserva fica presa e e devolvida (testes de reserva tardia).
        Order order = pending();
        order.confirm();
        StockReserved event = new StockReserved(UUID.randomUUID(), Instant.now(), 42L);
        when(inbox.firstTime(any(), any())).thenReturn(true);
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        saga.onStockReserved(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void produtoRemovidoApagaACopiaLocalDePreco() {
        saga.onProductChanged(ProductChanged.removed(5L));

        verify(snapshots).deleteById(5L);
    }

    @Test
    void produtoAlteradoAtualizaACopiaLocalDePreco() {
        saga.onProductChanged(ProductChanged.upserted(5L, "Teclado", 1500, 2L));

        ArgumentCaptor<ProductSnapshot> saved = ArgumentCaptor.forClass(ProductSnapshot.class);
        verify(snapshots).save(saved.capture());
        assertThat(saved.getValue().getPriceCents()).isEqualTo(1500);
    }

    @Test
    void upsertMaisAntigoQueACopiaLocalEIgnorado() {
        when(snapshots.findById(5L)).thenReturn(Optional.of(new ProductSnapshot(5L, "Teclado", 1500, 5L)));

        saga.onProductChanged(ProductChanged.upserted(5L, "Teclado velho", 900, 4L));

        verify(snapshots, never()).save(any());
    }

    @Test
    void upsertMaisNovoSubstituiACopiaLocal() {
        when(snapshots.findById(5L)).thenReturn(Optional.of(new ProductSnapshot(5L, "Teclado", 1500, 5L)));

        saga.onProductChanged(ProductChanged.upserted(5L, "Teclado", 1800, 6L));

        verify(snapshots).save(any(ProductSnapshot.class));
    }

    @Test
    void reservaTardiaDePedidoCanceladoPedeADevolucaoDoEstoque() {
        Order order = pending();
        order.cancel();
        StockReserved event = new StockReserved(UUID.randomUUID(), Instant.now(), 42L);
        when(inbox.firstTime(OrderSaga.RESERVED, event.eventId())).thenReturn(true);
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        saga.onStockReserved(event);

        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.STOCK_RELEASE), eq("42"), sent.capture());
        StockReleaseRequested release = (StockReleaseRequested) sent.getValue();
        assertThat(release.quantity()).isEqualTo(2);
        assertThat(release.productId()).isEqualTo(5L);
    }

    @Test
    void reservaTardiaDePedidoRecusadoPorTempoTambemDevolve() {
        Order order = pending();
        order.reject();
        StockReserved event = new StockReserved(UUID.randomUUID(), Instant.now(), 42L);
        when(inbox.firstTime(OrderSaga.RESERVED, event.eventId())).thenReturn(true);
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        saga.onStockReserved(event);

        verify(outbox).append(eq(Topics.STOCK_RELEASE), eq("42"), any(StockReleaseRequested.class));
    }

    @Test
    void reservaDuplicadaDePedidoJaConfirmadoNaoDevolveNada() {
        Order order = pending();
        order.confirm();
        StockReserved event = new StockReserved(UUID.randomUUID(), Instant.now(), 42L);
        when(inbox.firstTime(OrderSaga.RESERVED, event.eventId())).thenReturn(true);
        when(orders.findById(42L)).thenReturn(Optional.of(order));

        saga.onStockReserved(event);

        verify(outbox, never()).append(eq(Topics.STOCK_RELEASE), any(), any());
    }
}
