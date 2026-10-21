import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type { components } from './api/schema';

// Tipos GERADOS de api/openapi.yaml (npm run gen:api). Nao edite schema.d.ts a mao:
// mude o contrato, gere de novo, e o compilador aponta tudo que precisa mudar aqui.
type Schemas = components['schemas'];
export type Product = Schemas['Product'];
export type Order = Schemas['Order'];
export type Notification = Schemas['Notification'];
export type Me = Schemas['Me'];
export type AdminSummary = Schemas['AdminSummary'];

/** Chamadas ao backend. O token Bearer e anexado pelo authInterceptor. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  products(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/products');
  }

  orders(): Observable<Order[]> {
    return this.http.get<Order[]>('/api/orders');
  }

  notifications(): Observable<Notification[]> {
    return this.http.get<Notification[]>('/api/notifications');
  }

  me(): Observable<Me> {
    return this.http.get<Me>('/api/me');
  }

  adminSummary(): Observable<AdminSummary> {
    return this.http.get<AdminSummary>('/api/admin/summary');
  }

  createOrder(productId: number, quantity: number): Observable<Order> {
    const body: Schemas['CreateOrderRequest'] = { productId, quantity };
    return this.http.post<Order>('/api/orders', body);
  }
}
