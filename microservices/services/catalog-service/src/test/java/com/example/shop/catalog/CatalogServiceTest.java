package com.example.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.DomainEvent;
import com.example.shop.contracts.ProductChanged;
import com.example.shop.contracts.Topics;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class CatalogServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final Outbox outbox = mock(Outbox.class);
    private final ProductCache cache = mock(ProductCache.class);
    private final CatalogService service = new CatalogService(products, categories, outbox, cache);

    private static Product product(long id, long version) {
        Product p = new Product("SKU-1", "Teclado", 1000, 10);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "version", version);
        return p;
    }

    @Test
    void criaProdutoComCategoriaEAnuncia() {
        Category cat = new Category("Periféricos");
        ReflectionTestUtils.setField(cat, "id", 2L);
        when(categories.findById(2L)).thenReturn(Optional.of(cat));
        when(products.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 7L);
            return p;
        });

        ProductResponse created = service.create("Mouse", 5000, 4, 2L);

        assertThat(created.categoryName()).isEqualTo("Periféricos");
        verify(outbox).append(eq(Topics.PRODUCT_CHANGED), eq("7"), any(ProductChanged.class));
    }

    @Test
    void atualizaQuandoAVersaoConfere() {
        when(products.findByIdWithCategory(7L)).thenReturn(Optional.of(product(7L, 3L)));

        ProductResponse updated = service.update(7L, "Teclado novo", 1200, 8, null, 3L);

        assertThat(updated.name()).isEqualTo("Teclado novo");
        assertThat(updated.priceCents()).isEqualTo(1200);
        verify(outbox).append(eq(Topics.PRODUCT_CHANGED), eq("7"), any(ProductChanged.class));
    }

    @Test
    void versaoDesatualizadaViraConflitoESemEvento() {
        when(products.findByIdWithCategory(7L)).thenReturn(Optional.of(product(7L, 4L)));

        assertThatThrownBy(() -> service.update(7L, "X", 1, 1, null, 3L)).isInstanceOf(Conflict.class);

        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void excluirAvisaOsOutrosServicos() {
        Product p = product(7L, 0L);
        when(products.findById(7L)).thenReturn(Optional.of(p));

        service.delete(7L);

        verify(products).delete(p);
        ArgumentCaptor<DomainEvent> sent = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).append(eq(Topics.PRODUCT_CHANGED), eq("7"), sent.capture());
        ProductChanged event = (ProductChanged) sent.getValue();
        assertThat(event.productId()).isEqualTo(7L);
        assertThat(event.type()).isEqualTo(ProductChanged.Type.REMOVED);
    }

    @Test
    void categoriaInexistenteNaoCriaProduto() {
        when(categories.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("Mouse", 5000, 4, 99L)).isInstanceOf(Conflict.class);

        verify(products, never()).save(any());
    }

    @Test
    void categoriaComProdutosNaoPodeSerExcluida() {
        CategoryService categoryService = new CategoryService(categories, products);
        Category cat = new Category("Livros");
        when(categories.findById(1L)).thenReturn(Optional.of(cat));
        when(products.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L)).isInstanceOf(Conflict.class);

        verify(categories, never()).delete(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listagemLimitaOTamanhoDaPaginaEIgnoraCampoDeOrdenacaoDesconhecido() {
        when(products.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product(7L, 0L)), PageRequest.of(0, 100), 1));

        PageResponse<ProductResponse> page = service.search("tec", null, 0, 5000, "id; drop table products", true);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(products).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(CatalogService.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("name").isDescending()).isTrue();
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void leituraPorIdUsaOCacheEEvitaOBanco() {
        ProductResponse cached = ProductResponse.of(product(7L, 3L));
        when(cache.get(7L)).thenReturn(Optional.of(cached));

        assertThat(service.get(7L)).isEqualTo(cached);
        verify(products, never()).findByIdWithCategory(7L);
    }

    @Test
    void leituraPorIdSemCacheLeOBancoEGuardaNoCache() {
        Product p = product(7L, 3L);
        when(products.findByIdWithCategory(7L)).thenReturn(Optional.of(p));

        ProductResponse response = service.get(7L);

        verify(cache).put(response);
    }

    @Test
    void atualizarInvalidaOCache() {
        Product p = product(7L, 3L);
        when(products.findByIdWithCategory(7L)).thenReturn(Optional.of(p));

        service.update(7L, "Teclado novo", 1200, 5, null, 3L);

        verify(cache).evictAfterCommit(7L);
    }
}
