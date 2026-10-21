package com.example.shop.catalog;

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
import com.example.shop.contracts.OrderCreated;
import com.example.shop.contracts.StockRejected;
import com.example.shop.contracts.StockReleaseRequested;
import com.example.shop.contracts.StockReserved;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StockServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final StockReservationRepository reservations = mock(StockReservationRepository.class);
    private final Inbox inbox = mock(Inbox.class);
    private final Outbox outbox = mock(Outbox.class);
    private final ProductCache cache = mock(ProductCache.class);
    private final StockService service = new StockService(products, reservations, inbox, outbox, cache);

    private static OrderCreated order(long productId, int quantity) {
        return new OrderCreated(UUID.randomUUID(), Instant.now(), 7L, productId, quantity, "alice");
    }

    @Test
    void reservaEstoqueEPublicaStockReserved() {
        Product product = new Product("S", "Teclado", 1000, 10);
        OrderCreated event = order(1L, 3);
        when(inbox.firstTime(StockService.CONSUMER, event.eventId())).thenReturn(true);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        service.onOrderCreated(event);
        verify(cache).evictAfterCommit(1L);

        assertThat(product.getStock()).isEqualTo(7);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.STOCK_RESERVED), eq("7"), sent.capture());
        assertThat(sent.getValue()).isInstanceOf(StockReserved.class);
    }

    @Test
    void semSaldoRejeitaENaoAlteraEstoque() {
        Product product = new Product("S", "Teclado", 1000, 2);
        OrderCreated event = order(1L, 3);
        when(inbox.firstTime(any(), any())).thenReturn(true);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        service.onOrderCreated(event);

        assertThat(product.getStock()).isEqualTo(2);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.STOCK_REJECTED), eq("7"), sent.capture());
        assertThat(((StockRejected) sent.getValue()).reason()).isEqualTo("estoque insuficiente");
    }

    @Test
    void produtoInexistenteRejeita() {
        when(inbox.firstTime(any(), any())).thenReturn(true);
        when(products.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        service.onOrderCreated(order(99L, 1));

        verify(outbox).append(eq(Topics.STOCK_REJECTED), eq("7"), any(StockRejected.class));
    }

    @Test
    void eventoRepetidoEIgnorado() {
        OrderCreated event = order(1L, 1);
        when(inbox.firstTime(StockService.CONSUMER, event.eventId())).thenReturn(false);

        service.onOrderCreated(event);

        verify(products, never()).findByIdForUpdate(1L);
        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void reservaFicaRegistradaParaPoderSerDevolvida() {
        Product product = new Product("S", "Teclado", 1000, 10);
        OrderCreated event = order(1L, 3);
        when(inbox.firstTime(StockService.CONSUMER, event.eventId())).thenReturn(true);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        service.onOrderCreated(event);

        ArgumentCaptor<StockReservation> saved = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservations).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    void devolveOEstoqueReservadoUmaUnicaVez() {
        Product product = new Product("S", "Teclado", 1000, 7);
        StockReservation reservation = new StockReservation(7L, 1L, 3);
        StockReleaseRequested first = new StockReleaseRequested(UUID.randomUUID(), Instant.now(), 7L, 1L, 3, "cancelado");
        StockReleaseRequested again = new StockReleaseRequested(UUID.randomUUID(), Instant.now(), 7L, 1L, 3, "cancelado");
        when(inbox.firstTime(eq(StockService.RELEASE_CONSUMER), any(UUID.class))).thenReturn(true);
        when(reservations.findById(7L)).thenReturn(Optional.of(reservation));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        service.onStockRelease(first);
        service.onStockRelease(again); // outro evento para o mesmo pedido: nao devolve de novo

        assertThat(product.getStock()).isEqualTo(10);
        assertThat(reservation.isReleased()).isTrue();
    }

    @Test
    void devolucaoRepetidaPeloMesmoEventoEIgnorada() {
        StockReleaseRequested event = new StockReleaseRequested(UUID.randomUUID(), Instant.now(), 7L, 1L, 3, "cancelado");
        when(inbox.firstTime(StockService.RELEASE_CONSUMER, event.eventId())).thenReturn(false);

        service.onStockRelease(event);

        verify(reservations, never()).findById(any());
    }
}
