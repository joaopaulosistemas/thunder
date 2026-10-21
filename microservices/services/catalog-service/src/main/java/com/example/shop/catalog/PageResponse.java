package com.example.shop.catalog;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Pagina de resultados: {@code page} comeca em 0; {@code total} e a contagem de TODOS os itens do filtro. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements());
    }
}
