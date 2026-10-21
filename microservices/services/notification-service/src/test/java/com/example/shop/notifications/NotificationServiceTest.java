package com.example.shop.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shop.common.inbox.Inbox;
import com.example.shop.contracts.OrderCancelled;
import com.example.shop.contracts.OrderConfirmed;
import com.example.shop.contracts.OrderRejected;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final Inbox inbox = mock(Inbox.class);
    private final NotificationService service = new NotificationService(repository, inbox);

    @Test
    void confirmacaoGeraNotificacaoParaOCliente() {
        OrderConfirmed event = new OrderConfirmed(UUID.randomUUID(), Instant.now(), 42L, "alice", 4500L);
        when(inbox.firstTime(NotificationService.CONFIRMED, event.eventId())).thenReturn(true);

        service.onConfirmed(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCustomer()).isEqualTo("alice");
        assertThat(saved.getValue().getMessage()).contains("#42").contains("confirmado");
    }

    @Test
    void recusaInformaOMotivo() {
        OrderRejected event = new OrderRejected(UUID.randomUUID(), Instant.now(), 43L, "bob", "estoque insuficiente");
        when(inbox.firstTime(NotificationService.REJECTED, event.eventId())).thenReturn(true);

        service.onRejected(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).contains("recusado").contains("estoque insuficiente");
    }

    @Test
    void eventoRepetidoNaoDuplicaNotificacao() {
        OrderConfirmed event = new OrderConfirmed(UUID.randomUUID(), Instant.now(), 42L, "alice", 4500L);
        when(inbox.firstTime(any(), any())).thenReturn(false);

        service.onConfirmed(event);

        verify(repository, never()).save(any());
    }

    @Test
    void cancelamentoAvisaOCliente() {
        OrderCancelled event = new OrderCancelled(UUID.randomUUID(), Instant.now(), 44L, "alice", "cancelado a pedido");
        when(inbox.firstTime(NotificationService.CANCELLED, event.eventId())).thenReturn(true);

        service.onCancelled(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).contains("#44").contains("cancelado");
    }
}
