import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Abas do módulo. Links relativos: funcionam montado em /catalog (shell) ou na raiz (isolado). */
@Component({
  selector: 'catalog-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav aria-label="Catálogo">
      <a routerLink="." routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">Produtos</a>
      <a routerLink="categories" routerLinkActive="on">Categorias</a>
    </nav>
  `,
  styles: [
    `
      nav { display: flex; gap: 0.25rem; margin-bottom: 1rem; border-bottom: 1px solid var(--rule, #ddd); }
      a { padding: 0.5rem 0.9rem; color: var(--muted, #666); text-decoration: none; border-bottom: 2px solid transparent; margin-bottom: -1px; }
      a.on { color: var(--accent, #0b6e69); border-bottom-color: var(--accent, #0b6e69); font-weight: 600; }
    `,
  ],
})
export class CatalogNav {}
