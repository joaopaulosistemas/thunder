package com.example.shop.catalog.internal;

import com.example.shop.catalog.api.CatalogApi;
import com.example.shop.catalog.api.ProductView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
class ProductController {

    private final CatalogApi catalog;

    ProductController(CatalogApi catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<ProductView> list() {
        return catalog.listProducts();
    }

    @GetMapping("/{id}")
    ResponseEntity<ProductView> get(@PathVariable Long id) {
        return catalog.findProduct(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
