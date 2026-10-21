package com.example.shop.catalog;

import com.example.shop.common.outbox.Outbox;
import com.example.shop.contracts.ProductChanged;
import com.example.shop.contracts.Topics;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD de produtos. Todo create/update/delete registra o evento correspondente no outbox. */
@Service
public class CatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final Outbox outbox;
    private final ProductCache cache;

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_QUERY = 100;
    private static final Set<String> SORTABLE = Set.of("id", "name", "priceCents", "stock");

    public CatalogService(ProductRepository products, CategoryRepository categories, Outbox outbox,
            ProductCache cache) {
        this.products = products;
        this.categories = categories;
        this.outbox = outbox;
        this.cache = cache;
    }

    /**
     * Listagem paginada no servidor, com busca por nome (q) e filtro por categoria. O tamanho da
     * pagina e limitado a {@value #MAX_PAGE_SIZE} e o campo de ordenacao so aceita nomes conhecidos
     * (nunca o que o cliente mandar direto para a consulta).
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, Long categoryId, int page, int size, String sortField,
            boolean descending) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String field = sortField != null && SORTABLE.contains(sortField) ? sortField : "name";
        Sort sort = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, field).and(Sort.by("id"));
        var result = products.findAll(ProductSpecs.search(q == null || q.length() <= MAX_QUERY ? q : q.substring(0, MAX_QUERY), categoryId), PageRequest.of(Math.max(page, 0), safeSize, sort));
        return PageResponse.of(result, ProductResponse::of);
    }

    /** Leitura por id com cache: o cache so guarda o que veio do banco, e e invalidado em update/delete. */
    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return cache.get(id).orElseGet(() -> {
            ProductResponse fresh = products.findByIdWithCategory(id).map(ProductResponse::of)
                    .orElseThrow(() -> new ProductNotFound(id));
            cache.put(fresh);
            return fresh;
        });
    }

    @Transactional
    public ProductResponse create(String name, int priceCents, int stock, Long categoryId) {
        Product product = new Product("SKU-" + UUID.randomUUID().toString().substring(0, 8), name, priceCents, stock);
        product.update(name, priceCents, stock, category(categoryId));
        Product saved = products.save(product);
        announce(saved);
        return ProductResponse.of(saved);
    }

    /**
     * Atualiza um produto. {@code expectedVersion} e a versao que o usuario viu na tela: se outra pessoa
     * salvou antes, a versao mudou e devolvemos conflito (o Hibernate tambem confere ao gravar).
     */
    @Transactional
    public ProductResponse update(long id, String name, int priceCents, int stock, Long categoryId, long expectedVersion) {
        Product product = products.findByIdWithCategory(id).orElseThrow(() -> new ProductNotFound(id));
        if (product.getVersion() != expectedVersion) {
            throw new Conflict("O produto foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
        product.update(name, priceCents, stock, category(categoryId));
        products.flush(); // forca o UPDATE agora para o @Version ser conferido e incrementado
        cache.evictAfterCommit(id);
        announce(product);
        return ProductResponse.of(product);
    }

    /** Exclusao logica (ver @SQLDelete em Product) e aviso aos demais servicos. */
    @Transactional
    public void delete(long id) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFound(id));
        products.delete(product);
        cache.evictAfterCommit(id);
        outbox.append(Topics.PRODUCT_CHANGED, String.valueOf(id), ProductChanged.removed(id));
    }

    /** Reenvia todos os produtos (usado na partida): order-service faz upsert, entao repetir e inofensivo. */
    @Transactional
    public int announceAll() {
        List<Product> all = products.findAll();
        all.forEach(this::announce);
        return all.size();
    }

    private Category category(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findById(categoryId).orElseThrow(() -> new Conflict("Categoria " + categoryId + " nao existe"));
    }

    private void announce(Product p) {
        outbox.append(Topics.PRODUCT_CHANGED, String.valueOf(p.getId()),
                ProductChanged.upserted(p.getId(), p.getName(), p.getPriceCents(), p.getVersion()));
    }
}
