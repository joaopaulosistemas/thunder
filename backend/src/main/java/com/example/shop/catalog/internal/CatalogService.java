package com.example.shop.catalog.internal;

import com.example.shop.catalog.api.CatalogApi;
import com.example.shop.catalog.api.ProductView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CatalogService implements CatalogApi {

    private final ProductRepository repository;

    CatalogService(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    @Cacheable("products-list")
    public List<ProductView> listProducts() {
        return new ArrayList<>(repository.findAll().stream().map(CatalogService::toView).toList());
    }

    @Override
    @Cacheable(cacheNames = "products", key = "#id", unless = "#result == null")
    public Optional<ProductView> findProduct(Long id) {
        return repository.findById(id).map(CatalogService::toView);
    }

    private static ProductView toView(Product p) {
        return new ProductView(p.getId(), p.getSku(), p.getName(), p.getPriceCents());
    }
}
