import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogNav } from './catalog-nav';
import { CatalogApi, Category } from './catalog.api';
import { messageFor } from './errors';

@Component({
  selector: 'catalog-categories',
  imports: [FormsModule, CatalogNav],
  template: `
    <h1>Catálogo</h1>
    <catalog-nav />

    @if (message()) {
      <p class="msg" [class.err]="isError()" [attr.role]="isError() ? 'alert' : 'status'">{{ message() }}</p>
    }

    @if (isAdmin()) {
      <form class="editor inline" (ngSubmit)="create()" aria-label="Formulário de categoria">
        <label>Nova categoria
          <input name="newName" [(ngModel)]="newName" required maxlength="120" />
        </label>
        <button type="submit" class="primary">Adicionar</button>
      </form>
    }

    <div class="table-wrap">
      <table>
        <thead><tr><th>Categoria</th>@if (isAdmin()) { <th class="actions">Ações</th> }</tr></thead>
        <tbody>
          @for (c of categories(); track c.id) {
            <tr>
              <td>
                @if (editingId() === c.id) {
                  <input name="rename" [(ngModel)]="editName" maxlength="120" [attr.aria-label]="'Novo nome de ' + c.name" />
                } @else {
                  {{ c.name }}
                }
              </td>
              @if (isAdmin()) {
                <td class="actions">
                  @if (editingId() === c.id) {
                    <button type="button" class="primary" (click)="rename(c)">Salvar</button>
                    <button type="button" (click)="editingId.set(null)">Cancelar</button>
                  } @else if (confirmingId() === c.id) {
                    <span class="confirm">Excluir "{{ c.name }}"?
                      <button type="button" class="danger" (click)="remove(c)">Sim, excluir</button>
                      <button type="button" (click)="confirmingId.set(null)">Não</button>
                    </span>
                  } @else {
                    <button type="button" (click)="startRename(c)" [attr.aria-label]="'Renomear ' + c.name">Renomear</button>
                    <button type="button" (click)="confirmingId.set(c.id)" [attr.aria-label]="'Excluir ' + c.name">Excluir</button>
                  }
                </td>
              }
            </tr>
          } @empty {
            <tr><td [attr.colspan]="isAdmin() ? 2 : 1" class="empty">Nenhuma categoria cadastrada.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styleUrl: './crud.css',
})
export class CategoriesPage implements OnInit {
  private readonly api = inject(CatalogApi);

  readonly categories = signal<Category[]>([]);
  readonly roles = signal<string[]>([]);
  readonly isAdmin = computed(() => this.roles().includes('admin'));
  readonly editingId = signal<number | null>(null);
  readonly confirmingId = signal<number | null>(null);
  readonly message = signal('');
  readonly isError = signal(false);
  newName = '';
  editName = '';

  ngOnInit(): void {
    this.api.me().subscribe({ next: (me) => this.roles.set(me.roles), error: () => this.roles.set([]) });
    this.load();
  }

  create(): void {
    const name = this.newName.trim();
    if (!name) {
      return;
    }
    this.api.createCategory(name).subscribe({
      next: () => {
        this.newName = '';
        this.notify(`Categoria "${name}" criada.`, false);
        this.load();
      },
      error: (e) => this.notify(messageFor(e, 'Não foi possível criar a categoria.'), true),
    });
  }

  startRename(c: Category): void {
    this.editingId.set(c.id);
    this.editName = c.name;
  }

  rename(c: Category): void {
    const name = this.editName.trim();
    if (!name) {
      return;
    }
    this.api.renameCategory(c.id, name, c.version).subscribe({
      next: () => {
        this.editingId.set(null);
        this.notify(`Categoria renomeada para "${name}".`, false);
        this.load();
      },
      error: (e) => {
        this.editingId.set(null);
        this.notify(messageFor(e, 'Não foi possível renomear a categoria.'), true);
        this.load();
      },
    });
  }

  remove(c: Category): void {
    this.api.deleteCategory(c.id).subscribe({
      next: () => {
        this.confirmingId.set(null);
        this.notify(`Categoria "${c.name}" excluída.`, false);
        this.load();
      },
      error: (e) => {
        this.confirmingId.set(null);
        this.notify(messageFor(e, 'Não foi possível excluir a categoria.'), true);
      },
    });
  }

  private load(): void {
    this.api.categories().subscribe({
      next: (list) => this.categories.set(list),
      error: () => this.notify('Não foi possível carregar as categorias.', true),
    });
  }

  private notify(text: string, error: boolean): void {
    this.message.set(text);
    this.isError.set(error);
  }
}
