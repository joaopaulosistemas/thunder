import { HttpClient } from '@angular/common/http';
import { StampPipe } from './format';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';

interface NotificationItem {
  id: number;
  orderId: number;
  customer: string;
  message: string;
  createdAt: string;
}

/** Mesmo nome de evento publicado pelo microfrontend de pedidos (contrato por CustomEvent). */
const ORDER_PLACED_EVENT = 'shop:order-placed';

@Component({
  selector: 'notifications-page',
  imports: [StampPipe],
  template: `
    <h1>Notificações</h1>
    @if (error()) {
      <p class="err" role="alert">{{ error() }}</p>
    }
    <ul>
      @for (n of items(); track n.id) {
        <li>
          <span class="msg">{{ n.message }}</span>
          <span class="meta">pedido #{{ n.orderId }} · {{ n.customer }} · {{ n.createdAt | stamp }}</span>
        </li>
      } @empty {
        <li class="empty">Nenhuma notificação ainda. Faça um pedido para ver uma chegar.</li>
      }
    </ul>
  `,
  styles: [
    `
      h1 { margin: 0 0 1rem; font-size: 1.4rem; }
      ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.5rem; }
      li { display: grid; gap: 0.15rem; padding: 0.7rem 0.9rem; border: 1px solid var(--rule, #ddd); border-radius: 8px; background: var(--surface, #fff); }
      .meta { font-size: 0.8rem; color: var(--muted, #666); }
      .empty { color: var(--muted, #666); }
      .err { color: var(--bad, #b3261e); }
    `,
  ],
})
export class NotificationsPage implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private timer?: ReturnType<typeof setInterval>;
  private readonly onPlaced = () => setTimeout(() => this.load(), 1500);

  readonly items = signal<NotificationItem[]>([]);
  readonly error = signal('');

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.load(), 5000);
    window.addEventListener(ORDER_PLACED_EVENT, this.onPlaced);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
    window.removeEventListener(ORDER_PLACED_EVENT, this.onPlaced);
  }

  private load(): void {
    this.http.get<NotificationItem[]>('/api/notifications').subscribe({
      next: (list) => {
        this.items.set(list);
        this.error.set('');
      },
      error: () => this.error.set('Não foi possível carregar as notificações.'),
    });
  }
}
