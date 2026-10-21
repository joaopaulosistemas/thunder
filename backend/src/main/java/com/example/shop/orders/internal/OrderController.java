package com.example.shop.orders.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class OrderController {

    record CreateOrderRequest(@NotNull Long productId, @Min(1) @Max(1000) int quantity) {
    }

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    /** O cliente do pedido vem do token, nunca do corpo da requisicao. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return service.create(request.productId(), request.quantity(), authentication.getName());
    }

    @GetMapping("/orders")
    List<OrderResponse> latest(Authentication authentication) {
        return service.latest(authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/admin/summary")
    OrderService.AdminSummary summary() {
        return service.summary();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", e.getMessage());
    }

    static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }
}
