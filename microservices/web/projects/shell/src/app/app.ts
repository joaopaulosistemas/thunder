import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth/auth.service';
import { APP_BUILT_AT, APP_COMMIT, APP_VERSION } from './version';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly version = `v${APP_VERSION} · ${APP_COMMIT}`;
  protected readonly builtAt = APP_BUILT_AT;
}
