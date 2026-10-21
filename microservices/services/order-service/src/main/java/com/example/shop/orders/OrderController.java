package com.example.shop.orders;

import com.example.shop.common.security.Identity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    record CreateOrder(@Min(1) long productId, @Min(1) @Max(1000) int quantity) {
    }

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    /** 202: o pedido foi aceito, mas a confirmacao e assincrona (consulte o status). */
    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    OrderResponse create(@Valid @RequestBody CreateOrder request, Authentication auth) {
        return OrderResponse.of(service.place(auth.getName(), request.productId(), request.quantity()));
    }

    /** Cancela o proprio pedido (admin cancela qualquer um). 404 se nao for seu; 409 se ja foi decidido. */
    @PostMapping("/api/orders/{id}/cancel")
    OrderResponse cancel(@PathVariable long id, Authentication auth) {
        return OrderResponse.of(service.cancel(id, auth.getName(), Identity.isAdmin(auth)));
    }

    @GetMapping("/api/orders")
    List<OrderResponse> list(Authentication auth) {
        return service.recent(auth.getName(), Identity.isAdmin(auth)).stream().map(OrderResponse::of).toList();
    }

    @GetMapping("/api/admin/summary")
    OrderService.Summary summary() {
        return service.summary();
    }
}
