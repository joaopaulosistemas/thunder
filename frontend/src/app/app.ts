import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminSummary, ApiService, Me, Notification, Order, Product } from './api.service';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [FormsModule, CurrencyPipe, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly products = signal<Product[]>([]);
  protected readonly orders = signal<Order[]>([]);
  protected readonly notifications = signal<Notification[]>([]);
  protected readonly backendView = signal<Me | null>(null);
  protected readonly summary = signal<AdminSummary | null>(null);
  protected readonly message = signal('');
  protected readonly error = signal('');

  protected productId = 1;
  protected quantity = 1;

  ngOnInit(): void {
    this.api.products().subscribe({
      next: (p) => {
        this.products.set(p);
        if (p.length > 0) {
          this.productId = p[0].id;
        }
      },
      error: () => this.error.set('Nao foi possivel carregar o catalogo.'),
    });
    // O que o BACKEND enxergou no token: confirma que front e back concordam.
    this.api.me().subscribe({ next: (me) => this.backendView.set(me) });
    this.refreshOrders();
    this.refreshNotifications();
    this.refreshSummary();
  }

  protected order(): void {
    this.error.set('');
    this.message.set('');
    this.api.createOrder(this.productId, this.quantity).subscribe({
      next: (o) => {
        this.message.set(`Pedido #${o.id} criado.`);
        this.refreshOrders();
        this.refreshSummary();
        // o evento passa pelo Kafka de forma assincrona: atualiza a lista logo e de novo em seguida
        this.refreshNotifications();
        setTimeout(() => this.refreshNotifications(), 1500);
      },
      error: (e) => this.error.set(e?.error?.error ?? 'Falha ao criar o pedido.'),
    });
  }

  protected name(productId: number): string {
    return this.products().find((p) => p.id === productId)?.name ?? `Produto ${productId}`;
  }

  private refreshSummary(): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.api.adminSummary().subscribe({ next: (s) => this.summary.set(s) });
  }

  private refreshNotifications(): void {
    this.api.notifications().subscribe({
      next: (n) => this.notifications.set(n),
      error: () => this.notifications.set([]),
    });
  }

  private refreshOrders(): void {
    this.api.orders().subscribe({
      next: (o) => this.orders.set(o),
      error: () => this.error.set('Nao foi possivel carregar os pedidos.'),
    });
  }
}
