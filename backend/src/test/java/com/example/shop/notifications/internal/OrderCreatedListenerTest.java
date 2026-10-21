package com.example.shop.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OrderCreatedListenerTest {

    @Test
    void turnsJsonEventIntoNotification() throws Exception {
        NotificationStore store = new NotificationStore();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderCreatedListener listener = new OrderCreatedListener(store, new ObjectMapper(), registry);

        listener.handle("{\"orderId\":5,\"productId\":1,\"quantity\":2,\"totalCents\":17980,\"customer\":\"alice\"}");

        assertThat(store.latest()).hasSize(1);
        assertThat(store.latest().get(0).orderId()).isEqualTo(5L);
        assertThat(store.latest().get(0).customer()).isEqualTo("alice");
        assertThat(store.latest().get(0).message()).contains("#5").contains("alice");
        assertThat(registry.get("shop.notifications.processed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void redeliveredEventDoesNotDuplicateTheNotification() throws Exception {
        NotificationStore store = new NotificationStore();
        OrderCreatedListener listener = new OrderCreatedListener(store, new ObjectMapper(), new SimpleMeterRegistry());
        String payload = "{\"orderId\":5,\"totalCents\":100,\"customer\":\"alice\"}";

        listener.handle(payload);
        listener.handle(payload);

        assertThat(store.latest()).hasSize(1);
    }

    @Test
    void keepsOnlyTheLatestTwentyNotifications() throws Exception {
        NotificationStore store = new NotificationStore();
        OrderCreatedListener listener = new OrderCreatedListener(store, new ObjectMapper(), new SimpleMeterRegistry());

        for (int i = 1; i <= 25; i++) {
            listener.handle("{\"orderId\":" + i + ",\"totalCents\":100}");
        }

        assertThat(store.latest()).hasSize(20);
        assertThat(store.latest().get(0).orderId()).isEqualTo(25L);
    }
}
