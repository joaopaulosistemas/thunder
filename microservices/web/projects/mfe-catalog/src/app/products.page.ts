import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogNav } from './catalog-nav';
import { CatalogApi, Category, Product } from './catalog.api';
import { messageFor } from './errors';
import { BrlPipe } from './format';

interface Draft {
  id: number | null;
  version: number;
  name: string;
  price: number | null; // em reais; convertido para centavos ao salvar
  stock: number | null;
  categoryId: number | null;
}

const EMPTY: Draft = { id: null, version: 0, name: '', price: null, stock: 0, categoryId: null };

@Component({
  selector: 'catalog-products',
  imports: [BrlPipe, FormsModule, CatalogNav],
  template: `
    <h1>Catálogo</h1>
    <catalog-nav />

    @if (message()) {
      <p class="msg" [class.err]="isError()" [attr.role]="isError() ? 'alert' : 'status'">{{ message() }}</p>
    }

    <div class="filters">
      <label>Buscar
        <input name="q" placeholder="nome do produto" [ngModel]="query()" (ngModelChange)="onQuery($event)" />
      </label>
      <label>Categoria
        <select name="cat" [ngModel]="filterCategory()" (ngModelChange)="onCategory($event)">
          <option [ngValue]="null">Todas</option>
          @for (c of categories(); track c.id) {
            <option [ngValue]="c.id">{{ c.name }}</option>
          }
        </select>
      </label>
      @if (isAdmin()) {
        <button type="button" class="primary" (click)="startCreate()">Novo produto</button>
      }
    </div>

    @if (draft(); as d) {
      <form class="editor" (ngSubmit)="save()" aria-label="Formulário de produto">
        <h2>{{ d.id === null ? 'Novo produto' : 'Editar produto #' + d.id }}</h2>
        <label>Nome
          <input name="name" [(ngModel)]="d.name" required maxlength="255" />
        </label>
        <label>Preço (R$)
          <input name="price" type="number" min="0.01" step="0.01" [(ngModel)]="d.price" required />
        </label>
        <label>Estoque
          <input name="stock" type="number" min="0" step="1" [(ngModel)]="d.stock" required />
        </label>
        <label>Categoria
          <select name="category" [(ngModel)]="d.categoryId">
            <option [ngValue]="null">Sem categoria</option>
            @for (c of categories(); track c.id) {
              <option [ngValue]="c.id">{{ c.name }}</option>
            }
          </select>
        </label>
        <div class="row">
          <button type="submit" class="primary" [disabled]="saving()">Salvar</button>
          <button type="button" (click)="draft.set(null)">Cancelar</button>
        </div>
      </form>
    }

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Produto</th><th>Categoria</th><th class="num">Preço</th><th class="num">Estoque</th>
            @if (isAdmin()) { <th class="actions">Ações</th> }
          </tr>
        </thead>
        <tbody>
          @for (p of products(); track p.id) {
            <tr>
              <td>{{ p.name }}</td>
              <td>{{ p.categoryName ?? '—' }}</td>
              <td class="num">{{ p.priceCents | brl }}</td>
              <td class="num" [class.low]="p.stock < 5">{{ p.stock }}</td>
              @if (isAdmin()) {
                <td class="actions">
                  @if (confirmingId() === p.id) {
                    <span class="confirm">Excluir "{{ p.name }}"?
                      <button type="button" class="danger" (click)="remove(p)">Sim, excluir</button>
                      <button type="button" (click)="confirmingId.set(null)">Não</button>
                    </span>
                  } @else {
                    <button type="button" (click)="startEdit(p)" [attr.aria-label]="'Editar ' + p.name">Editar</button>
                    <button type="button" (click)="confirmingId.set(p.id)" [attr.aria-label]="'Excluir ' + p.name">Excluir</button>
                  }
                </td>
              }
            </tr>
          } @empty {
            <tr><td [attr.colspan]="isAdmin() ? 5 : 4" class="empty">Nenhum produto encontrado.</td></tr>
          }
        </tbody>
      </table>
    </div>
    <nav class="pager" aria-label="Paginação do catálogo">
      <button type="button" (click)="go(page() - 1)" [disabled]="page() === 0">Anterior</button>
      <span aria-live="polite">Página {{ page() + 1 }} de {{ pages() }} ({{ total() }} produtos)</span>
      <button type="button" (click)="go(page() + 1)" [disabled]="page() + 1 >= pages()">Próxima</button>
    </nav>
    @if (!isAdmin()) {
      <p class="hint">Somente administradores criam, editam e excluem produtos.</p>
    }
  `,
  styleUrl: './crud.css',
})
export class ProductsPage implements OnInit, OnDestroy {
  private readonly api = inject(CatalogApi);

  readonly products = signal<Product[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly roles = signal<string[]>([]);
  readonly query = signal('');
  readonly filterCategory = signal<number | null>(null);
  readonly draft = signal<Draft | null>(null);
  readonly confirmingId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly isError = signal(false);

  readonly isAdmin = computed(() => this.roles().includes('admin'));
  readonly page = signal(0);
  readonly total = signal(0);
  readonly size = 10;
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / this.size)));
  private searchTimer?: ReturnType<typeof setTimeout>;

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
  }

  ngOnInit(): void {
    this.api.me().subscribe({ next: (me) => this.roles.set(me.roles), error: () => this.roles.set([]) });
    this.api.categories().subscribe({ next: (list) => this.categories.set(list) });
    this.load();
  }

  /** Busca no servidor, com um pequeno atraso para não consultar a cada tecla. */
  onQuery(value: string): void {
    this.query.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page.set(0);
      this.load();
    }, 300);
  }

  onCategory(value: number | null): void {
    this.filterCategory.set(value);
    this.page.set(0);
    this.load();
  }

  go(page: number): void {
    this.page.set(Math.min(Math.max(page, 0), this.pages() - 1));
    this.load();
  }

  startCreate(): void {
    this.message.set('');
    this.draft.set({ ...EMPTY });
  }

  startEdit(p: Product): void {
    this.message.set('');
    this.draft.set({
      id: p.id,
      version: p.version,
      name: p.name,
      price: p.priceCents / 100,
      stock: p.stock,
      categoryId: p.categoryId,
    });
  }

  save(): void {
    const d = this.draft();
    if (!d || !d.name.trim() || d.price === null || d.stock === null) {
      return;
    }
    const input = {
      name: d.name.trim(),
      priceCents: Math.round(d.price * 100),
      stock: d.stock,
      categoryId: d.categoryId,
    };
    this.saving.set(true);
    const call = d.id === null ? this.api.createProduct(input) : this.api.updateProduct(d.id, input, d.version);
    call.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.draft.set(null);
        this.notify(d.id === null ? `Produto "${saved.name}" criado.` : `Produto "${saved.name}" atualizado.`, false);
        this.load();
      },
      error: (e) => {
        this.saving.set(false);
        this.notify(messageFor(e, 'Não foi possível salvar o produto.'), true);
        if (d.id !== null) {
          this.load(); // mostra a versão mais recente depois de um conflito
        }
      },
    });
  }

  remove(p: Product): void {
    this.api.deleteProduct(p.id).subscribe({
      next: () => {
        this.confirmingId.set(null);
        this.notify(`Produto "${p.name}" excluído.`, false);
        this.load();
      },
      error: (e) => {
        this.confirmingId.set(null);
        this.notify(messageFor(e, 'Não foi possível excluir o produto.'), true);
      },
    });
  }

  private load(): void {
    this.api
      .products({ q: this.query(), categoryId: this.filterCategory(), page: this.page(), size: this.size, sort: 'name,asc' })
      .subscribe({
      next: (result) => {
        this.products.set(result.items);
        this.total.set(result.total);
        // Se a última página ficou vazia (ex.: excluiu o último item dela), volta uma.
        if (result.items.length === 0 && this.page() > 0) {
          this.go(this.page() - 1);
        }
      },
      error: () => this.notify('Não foi possível carregar o catálogo.', true),
    });
  }

  private notify(text: string, error: boolean): void {
    this.message.set(text);
    this.isError.set(error);
  }
}
