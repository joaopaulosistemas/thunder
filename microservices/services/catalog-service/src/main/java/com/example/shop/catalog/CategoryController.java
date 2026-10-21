package com.example.shop.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CategoryController {

    record CategoryInput(@NotBlank @Size(max = 120) String name) {
    }

    record CategoryUpdate(@NotBlank @Size(max = 120) String name, long version) {
    }

    private final CategoryService service;

    CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping("/api/categories")
    List<CategoryResponse> list() {
        return service.list();
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@Valid @RequestBody CategoryInput in) {
        return service.create(in.name().trim());
    }

    @PutMapping("/api/admin/categories/{id}")
    CategoryResponse rename(@PathVariable long id, @Valid @RequestBody CategoryUpdate in) {
        return service.rename(id, in.name().trim(), in.version());
    }

    @DeleteMapping("/api/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
