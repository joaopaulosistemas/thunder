import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrlPipe, StampPipe } from './format';
import { Order, OrdersApi, ProductOption, Summary } from './orders.api';

/** Contrato entre microfrontends: avisa os outros (ex.: notificações) que há pedido novo. */
export const ORDER_PLACED_EVENT = 'shop:order-placed';

const LABEL: Record<Order['status'], string> = {
  PENDING: 'Processando',
  CONFIRMED: 'Confirmado',
  REJECTED: 'Recusado',
  CANCELLED: 'Cancelado',
};

@Component({
  selector: 'orders-page',
  imports: [BrlPipe, StampPipe, FormsModule],
  template: `
    <h1>Pedidos</h1>

    @if (isAdmin() && summary(); as s) {
      <div class="summary">
        <div><span>Pedidos</span><strong>{{ s.orders }}</strong></div>
        <div><span>Confirmados</span><strong>{{ s.confirmed }}</strong></div>
        <div><span>Receita</span><strong>{{ s.totalRevenueCents | brl }}</strong></div>
      </div>
    }

    <form class="new" (ngSubmit)="place()">
      <label>Produto
        <select name="product" [(ngModel)]="productId" required>
          @for (p of products(); track p.id) {
            <option [ngValue]="p.id">{{ p.name }} ({{ p.priceCents | brl }})</option>
          }
        </select>
      </label>
      <label>Quantidade
        <input name="qty" type="number" min="1" [(ngModel)]="quantity" required />
      </label>
      <button type="submit" [disabled]="busy() || !productId">Fazer pedido</button>
    </form>
    @if (message()) {
      <p class="msg" role="status">{{ message() }}</p>
    }

    <div class="table-wrap">
      <table>
        <thead><tr><th>#</th><th>Cliente</th><th class="num">Qtd</th><th class="num">Total</th><th>Status</th><th>Criado</th><th><span class="sr-only">Ações</span></th></tr></thead>
        <tbody>
          @for (o of orders(); track o.id) {
            <tr>
              <td>{{ o.id }}</td>
              <td>{{ o.customer }}</td>
              <td class="num">{{ o.quantity }}</td>
              <td class="num">{{ o.totalCents | brl }}</td>
              <td><span class="pill" [class]="'pill ' + o.status">{{ label(o.status) }}</span></td>
              <td>{{ o.createdAt | stamp }}</td>
              <td>
                @if (o.status === 'PENDING' || o.status === 'CONFIRMED') {
                  <button type="button" class="link" [disabled]="busy()" (click)="cancel(o)"
                    [attr.aria-label]="'Cancelar pedido ' + o.id">Cancelar</button>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="7" class="empty">Nenhum pedido ainda.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [
    `
      h1 { margin: 0 0 1rem; font-size: 1.4rem; }
      .summary { display: flex; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1rem; }
      .summary div { flex: 1 1 140px; border: 1px solid var(--rule, #ddd); border-radius: 8px; padding: 0.6rem 0.9rem; background: var(--surface, #fff); display: grid; }
      .summary span { font-size: 0.8rem; color: var(--muted, #666); }
      .summary strong { font-size: 1.25rem; font-variant-numeric: tabular-nums; }
      .new { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: end; margin-bottom: 0.75rem; }
      label { display: grid; gap: 0.2rem; font-size: 0.85rem; color: var(--muted, #666); }
      input, select { font: inherit; padding: 0.4rem 0.6rem; border: 1px solid var(--rule, #ccc); border-radius: 6px; background: var(--surface, #fff); color: var(--fg, #111); min-width: 0; }
      button { font: inherit; padding: 0.45rem 1rem; border: 0; border-radius: 6px; background: var(--accent, #0b6e69); color: #fff; cursor: pointer; }
      button:disabled { opacity: 0.5; cursor: default; }
      .msg { margin: 0 0 1rem; color: var(--muted, #666); }
      .table-wrap { overflow-x: auto; border: 1px solid var(--rule, #ddd); border-radius: 8px; background: var(--surface, #fff); }
      table { width: 100%; border-collapse: collapse; }
      th, td { padding: 0.55rem 0.8rem; text-align: left; border-bottom: 1px solid var(--rule, #ddd); white-space: nowrap; }
      tr:last-child td { border-bottom: 0; }
      .num { text-align: right; font-variant-numeric: tabular-nums; }
      .empty { color: var(--muted, #666); text-align: center; }
      .pill { padding: 0.1rem 0.6rem; border-radius: 999px; font-size: 0.8rem; }
      .PENDING { background: var(--warn-bg, #fdf3e0); color: var(--warn, #b7791f); }
      .CONFIRMED { background: var(--good-bg, #e0f3e6); color: var(--good, #1b7a3a); }
      .REJECTED { background: var(--bad-bg, #fbe5e3); color: var(--bad, #b3261e); }
      .CANCELLED { background: var(--rule, #eee); color: var(--muted, #555); }
      .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
      button.link { background: none; border: 0; padding: 0.25rem 0.4rem; color: var(--edge, #0b6b5d); text-decoration: underline; cursor: pointer; font: inherit; }
    `,
  ],
})
export class OrdersPage implements OnInit, OnDestroy {
  private readonly api = inject(OrdersApi);
  private timer?: ReturnType<typeof setInterval>;

  readonly orders = signal<Order[]>([]);
  readonly products = signal<ProductOption[]>([]);
  readonly roles = signal<string[]>([]);
  readonly summary = signal<Summary | null>(null);
  readonly isAdmin = computed(() => this.roles().includes('admin'));
  readonly busy = signal(false);
  readonly message = signal('');
  productId: number | null = null;
  quantity = 1;

  ngOnInit(): void {
    this.api.products().subscribe((list) => {
      this.products.set(list);
      this.productId = list[0]?.id ?? null;
    });
    this.api.me().subscribe({
      next: (me) => {
        this.roles.set(me.roles);
        this.refreshSummary();
      },
      error: () => this.roles.set([]),
    });
    this.refresh();
    // O pedido é confirmado de forma assíncrona (Kafka): atualiza enquanto houver pendentes.
    this.timer = setInterval(() => {
      if (this.orders().some((o) => o.status === 'PENDING')) {
        this.refresh();
      }
    }, 2000);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  label(status: Order['status']): string {
    return LABEL[status];
  }

  cancel(order: Order): void {
    this.busy.set(true);
    this.api.cancel(order.id).subscribe({
      next: () => {
        this.message.set(`Pedido #${order.id} cancelado.`);
        this.busy.set(false);
        this.refresh();
      },
      error: () => {
        this.message.set('Não foi possível cancelar o pedido (ele já pode ter sido decidido).');
        this.busy.set(false);
        this.refresh();
      },
    });
  }

  place(): void {
    if (!this.productId) {
      return;
    }
    this.busy.set(true);
    this.api.place(this.productId, this.quantity).subscribe({
      next: (order) => {
        this.message.set(`Pedido #${order.id} recebido. A confirmação chega em instantes.`);
        this.busy.set(false);
        window.dispatchEvent(new CustomEvent(ORDER_PLACED_EVENT, { detail: { orderId: order.id } }));
        this.refresh();
      },
      error: () => {
        this.message.set('Não foi possível enviar o pedido.');
        this.busy.set(false);
      },
    });
  }

  private refresh(): void {
    this.api.orders().subscribe((list) => {
      this.orders.set(list);
      this.refreshSummary();
    });
  }

  private refreshSummary(): void {
    if (this.isAdmin()) {
      this.api.summary().subscribe((s) => this.summary.set(s));
    }
  }
}
