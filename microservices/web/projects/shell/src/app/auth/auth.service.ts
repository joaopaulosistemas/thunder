import { Injectable, computed, signal } from '@angular/core';
import Keycloak from 'keycloak-js';

interface AuthConfig {
  keycloakUrl: string;
  realm: string;
  clientId: string;
}

/**
 * Login OIDC com Authorization Code + PKCE (S256) contra o Keycloak.
 * O access token fica so em memoria (nao vai para localStorage) e e renovado
 * com o refresh token antes de expirar.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private keycloak?: Keycloak;

  readonly username = signal('');
  readonly roles = signal<string[]>([]);
  readonly tokenExpiresAt = signal<Date | null>(null);
  readonly isAdmin = computed(() => this.roles().includes('admin'));

  /** Chamado no bootstrap: se nao ha sessao, redireciona para a tela de login do Keycloak. */
  async init(): Promise<void> {
    const config = await this.loadConfig();
    this.keycloak = new Keycloak({
      url: config.keycloakUrl,
      realm: config.realm,
      clientId: config.clientId,
    });
    const authenticated = await this.keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
    if (!authenticated) {
      await this.keycloak.login();
      return;
    }
    this.syncProfile();
  }

  /** Access token valido (renova se faltar menos de 30 s para expirar). */
  async token(): Promise<string> {
    const keycloak = this.keycloak;
    if (!keycloak) {
      throw new Error('AuthService nao inicializado');
    }
    try {
      await keycloak.updateToken(30);
    } catch {
      await keycloak.login();
    }
    this.syncProfile();
    return keycloak.token ?? '';
  }

  login(): void {
    void this.keycloak?.login();
  }

  logout(): void {
    void this.keycloak?.logout({ redirectUri: window.location.origin });
  }

  private syncProfile(): void {
    const parsed = this.keycloak?.tokenParsed;
    this.username.set((parsed?.['preferred_username'] as string | undefined) ?? '');
    const realmAccess = parsed?.['realm_access'] as { roles?: string[] } | undefined;
    this.roles.set(realmAccess?.roles ?? []);
    this.tokenExpiresAt.set(parsed?.exp ? new Date(parsed.exp * 1000) : null);
  }

  private async loadConfig(): Promise<AuthConfig> {
    const response = await fetch('/config.json', { cache: 'no-store' });
    if (!response.ok) {
      throw new Error('Nao foi possivel carregar /config.json');
    }
    return (await response.json()) as AuthConfig;
  }
}
