package com.example.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String forwarded(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        filter.filter(exchange, e -> { seen.set(e); return Mono.empty(); }).block();
        return seen.get().getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
    }

    @Test
    void geraIdQuandoNaoVem() {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        String id = forwarded(ex);
        assertThat(id).isNotBlank();
        assertThat(ex.getResponse().getHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void preservaIdRecebido() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").header(CorrelationIdFilter.HEADER, "abc-123").build());
        assertThat(forwarded(ex)).isEqualTo("abc-123");
    }

    @Test
    void descartaIdAbusivamenteGrande() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/x").header(CorrelationIdFilter.HEADER, "x".repeat(200)).build());
        assertThat(forwarded(ex)).hasSizeLessThanOrEqualTo(64);
    }
}
