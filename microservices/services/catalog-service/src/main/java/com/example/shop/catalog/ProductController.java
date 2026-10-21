package com.example.shop.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Leitura: qualquer usuario autenticado. Escrita: so admin (/api/admin/**, regra no SecurityConfig). */
@RestController
class ProductController {

    record ProductInput(@NotBlank @Size(max = 255) String name, @Min(1) @Max(100_000_000) int priceCents,
            @Min(0) @Max(1_000_000) int stock, Long categoryId) {
    }

    record ProductUpdate(@NotBlank @Size(max = 255) String name, @Min(1) @Max(100_000_000) int priceCents,
            @Min(0) @Max(1_000_000) int stock, Long categoryId, long version) {
    }

    private final CatalogService service;

    ProductController(CatalogService service) {
        this.service = service;
    }

    /**
     * Lista paginada. Parametros: q (busca no nome), categoryId, page (de 0), size (1 a 100, padrao 10)
     * e sort (campo[,asc|desc]; campos: name, priceCents, stock, id).
     */
    @GetMapping("/api/products")
    PageResponse<ProductResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "name,asc") String sort) {
        String[] parts = sort.split(",");
        return service.search(q, categoryId, page, size, parts[0].trim(),
                parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()));
    }

    @GetMapping("/api/products/{id}")
    ProductResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/api/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    ProductResponse create(@Valid @RequestBody ProductInput in) {
        return service.create(in.name(), in.priceCents(), in.stock(), in.categoryId());
    }

    @PutMapping("/api/admin/products/{id}")
    ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductUpdate in) {
        return service.update(id, in.name(), in.priceCents(), in.stock(), in.categoryId(), in.version());
    }

    @DeleteMapping("/api/admin/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
