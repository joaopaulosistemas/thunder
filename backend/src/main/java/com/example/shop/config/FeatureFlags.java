package com.example.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature flags simples via configuracao (FEATURES_BULK_DISCOUNT=true).
 * Separam "codigo integrado" de "funcionalidade liberada" (trunk-based).
 */
@ConfigurationProperties(prefix = "features")
public record FeatureFlags(boolean bulkDiscount) {
}
