import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface Category {
  id: number;
  name: string;
  version: number;
}

export interface Product {
  id: number;
  name: string;
  priceCents: number;
  stock: number;
  categoryId: number | null;
  categoryName: string | null;
  version: number;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface ProductQuery {
  q: string;
  categoryId: number | null;
  page: number;
  size: number;
  sort: string;
}

export interface ProductInput {
  name: string;
  priceCents: number;
  stock: number;
  categoryId: number | null;
}

export interface Me {
  username: string;
  roles: string[];
}

/** CRUD completo de produtos e categorias, falando só com o catalog-service (via gateway). */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  me() {
    return this.http.get<Me>('/api/me');
  }

  // Produtos
  /** Listagem paginada, com busca e filtro feitos no servidor (o catálogo pode ter milhares de itens). */
  products(query: ProductQuery) {
    const params: Record<string, string> = {
      page: String(query.page),
      size: String(query.size),
      sort: query.sort,
    };
    if (query.q.trim()) {
      params['q'] = query.q.trim();
    }
    if (query.categoryId !== null) {
      params['categoryId'] = String(query.categoryId);
    }
    return this.http.get<Page<Product>>('/api/products', { params });
  }
  createProduct(input: ProductInput) {
    return this.http.post<Product>('/api/admin/products', input);
  }
  /** `version` é a versão que a pessoa viu na tela: serve ao bloqueio otimista do backend. */
  updateProduct(id: number, input: ProductInput, version: number) {
    return this.http.put<Product>(`/api/admin/products/${id}`, { ...input, version });
  }
  deleteProduct(id: number) {
    return this.http.delete<void>(`/api/admin/products/${id}`);
  }

  // Categorias
  categories() {
    return this.http.get<Category[]>('/api/categories');
  }
  createCategory(name: string) {
    return this.http.post<Category>('/api/admin/categories', { name });
  }
  renameCategory(id: number, name: string, version: number) {
    return this.http.put<Category>(`/api/admin/categories/${id}`, { name, version });
  }
  deleteCategory(id: number) {
    return this.http.delete<void>(`/api/admin/categories/${id}`);
  }
}
