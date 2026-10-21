package com.example.shop.catalog.api;

import java.util.List;
import java.util.Optional;

/**
 * Contrato publico do modulo catalog. Outros modulos so podem depender
 * deste pacote (regra verificada por ArchUnit), nunca de catalog.internal.
 */
public interface CatalogApi {

    List<ProductView> listProducts();

    Optional<ProductView> findProduct(Long id);
}
