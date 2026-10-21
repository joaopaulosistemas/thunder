package com.example.shop.catalog;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryResponse::of).toList();
    }

    @Transactional
    public CategoryResponse create(String name) {
        if (categories.existsByNameIgnoreCase(name)) {
            throw new Conflict("Ja existe uma categoria chamada \"" + name + "\"");
        }
        return CategoryResponse.of(categories.save(new Category(name)));
    }

    @Transactional
    public CategoryResponse rename(long id, String name, long expectedVersion) {
        Category category = categories.findById(id).orElseThrow(() -> new ProductNotFound(id));
        if (category.getVersion() != expectedVersion) {
            throw new Conflict("A categoria foi alterada por outra pessoa. Recarregue e tente de novo.");
        }
        category.rename(name);
        categories.flush();
        return CategoryResponse.of(category);
    }

    /** Nao exclui categoria que ainda tem produtos: seria deixar produtos sem categoria sem querer. */
    @Transactional
    public void delete(long id) {
        Category category = categories.findById(id).orElseThrow(() -> new ProductNotFound(id));
        if (products.existsByCategoryId(id)) {
            throw new Conflict("A categoria \"" + category.getName() + "\" ainda tem produtos. Mova ou exclua os produtos antes.");
        }
        categories.delete(category);
    }
}
