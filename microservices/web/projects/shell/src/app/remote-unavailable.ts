import { Component } from '@angular/core';

/** Mostrado no lugar de um microfrontend que nao carregou: o resto do shell continua funcionando. */
@Component({
  selector: 'app-remote-unavailable',
  template: `
    <section class="unavailable" role="alert">
      <h2>Módulo indisponível</h2>
      <p>Este módulo não carregou agora. Os outros continuam funcionando; tente de novo em instantes.</p>
    </section>
  `,
  styles: [
    `
      .unavailable {
        border: 1px solid var(--warn, #b7791f);
        background: var(--warn-bg, #fdf3e0);
        color: var(--fg, #1d2b36);
        padding: 1rem 1.25rem;
        border-radius: 8px;
      }
      h2 {
        margin: 0 0 0.25rem;
        font-size: 1.05rem;
      }
      p {
        margin: 0;
      }
    `,
  ],
})
export class RemoteUnavailable {}
