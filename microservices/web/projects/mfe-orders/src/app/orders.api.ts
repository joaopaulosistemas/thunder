import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED';

export interface Order {
  id: number;
  customer: string;
  productId: number;
  quantity: number;
  totalCents: number;
  status: OrderStatus;
  createdAt: string;
}

export interface ProductOption {
  id: number;
  name: string;
  priceCents: number;
}

export interface Summary {
  orders: number;
  confirmed: number;
  totalRevenueCents: number;
}

/** Fala só com o order-service (e lê a lista de produtos para montar o formulário). */
@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);

  orders() {
    return this.http.get<Order[]>('/api/orders');
  }
  /** Opções do formulário: as primeiras 100 por nome (a API é paginada). */
  products() {
    return this.http
      .get<{ items: ProductOption[] }>('/api/products', { params: { size: '100', sort: 'name,asc' } })
      .pipe(map((page) => page.items));
  }
  place(productId: number, quantity: number) {
    return this.http.post<Order>('/api/orders', { productId, quantity });
  }
  cancel(id: number) {
    return this.http.post<Order>(`/api/orders/${id}/cancel`, {});
  }
  summary() {
    return this.http.get<Summary>('/api/admin/summary');
  }
  me() {
    return this.http.get<{ roles: string[] }>('/api/me');
  }
}
