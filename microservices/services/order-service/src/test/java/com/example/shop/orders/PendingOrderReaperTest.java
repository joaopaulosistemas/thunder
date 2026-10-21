package com.example.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.DomainEvent;
import com.example.shop.contracts.OrderRejected;
import com.example.shop.contracts.Topics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PendingOrderReaperTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final Outbox outbox = mock(Outbox.class);
    private final PendingOrderReaper reaper = new PendingOrderReaper(orders, outbox, Duration.ofMinutes(2), 100);

    @Test
    void recusaPedidosPendentesAlemDoLimiteEAvisaOCliente() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        Order stuck = new Order("alice", 5L, 2, 3000L);
        ReflectionTestUtils.setField(stuck, "id", 42L);
        when(orders.lockExpiredPending(now.minus(Duration.ofMinutes(2)), 100)).thenReturn(List.of(stuck));

        int count = reaper.expirePending(now);

        assertThat(count).isEqualTo(1);
        assertThat(stuck.getStatus()).isEqualTo(OrderStatus.REJECTED);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.ORDER_REJECTED), eq("42"), sent.capture());
        assertThat(((OrderRejected) sent.getValue()).reason()).isEqualTo(PendingOrderReaper.REASON);
    }

    @Test
    void semPedidosExpiradosNaoFazNada() {
        when(orders.lockExpiredPending(any(), eq(100))).thenReturn(List.of());

        assertThat(reaper.expirePending(Instant.now())).isZero();
        verify(outbox, never()).append(any(), any(), any());
    }
}
