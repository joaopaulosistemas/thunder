package com.example.shop.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache (Redis) da leitura de um produto por id. E so uma otimizacao: qualquer falha do Redis vira
 * "nao achou" e a leitura segue para o banco. A invalidacao roda DEPOIS do commit, senao outra
 * requisicao poderia reler o valor antigo e recoloca-lo no cache.
 */
@Component
public class ProductCache {

    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public ProductCache(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${app.cache.product-ttl:PT60S}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    private static String key(long id) {
        return "catalog:product:" + id;
    }

    public Optional<ProductResponse> get(long id) {
        try {
            String json = redis.opsForValue().get(key(id));
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, ProductResponse.class));
        } catch (Exception e) {
            log.warn("Cache indisponivel na leitura do produto {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    public void put(ProductResponse product) {
        try {
            redis.opsForValue().set(key(product.id()), mapper.writeValueAsString(product), ttl);
        } catch (Exception e) {
            log.warn("Cache indisponivel ao gravar o produto {}: {}", product.id(), e.toString());
        }
    }

    /** Apaga a entrada quando a transacao atual confirmar (ou ja, se nao houver transacao). */
    public void evictAfterCommit(long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(id);
                }
            });
        } else {
            evict(id);
        }
    }

    void evict(long id) {
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("Cache indisponivel ao invalidar o produto {}: {}", id, e.toString());
        }
    }
}
