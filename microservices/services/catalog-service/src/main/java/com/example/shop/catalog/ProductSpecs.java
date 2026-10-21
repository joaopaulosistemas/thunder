package com.example.shop.catalog;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionais da listagem. Specification evita o problema de parametro nulo tipado no Postgres. */
final class ProductSpecs {

    private ProductSpecs() {
    }

    static Specification<Product> search(String q, Long categoryId) {
        return (root, query, cb) -> {
            // Traz a categoria junto (sem N+1), mas nao na consulta de contagem da paginacao.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category", JoinType.LEFT);
            }
            List<Predicate> all = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + escape(q.trim().toLowerCase()) + "%";
                all.add(cb.like(cb.lower(root.get("name")), like, '\\'));
            }
            if (categoryId != null) {
                all.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    /** % e _ digitados pelo usuario viram texto comum, nao curinga. */
    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
