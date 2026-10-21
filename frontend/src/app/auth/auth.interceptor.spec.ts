import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

/** Deixa as promises do token resolverem antes de olhar a requisicao. */
const flush = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['token', 'login']);
    auth.token.and.resolveTo('abc.def.ghi');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('anexa o token Bearer nas chamadas /api', async () => {
    http.get('/api/products').subscribe();
    await flush();

    const req = controller.expectOne('/api/products');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    req.flush([]);
  });

  it('nao envia o token para o que nao e /api (evita vazar para outros destinos)', async () => {
    http.get('/config.json').subscribe();
    http.get('https://terceiro.example.com/dados').subscribe();
    await flush();

    for (const req of controller.match(() => true)) {
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush({});
    }
    expect(auth.token).not.toHaveBeenCalled();
  });

  it('volta para o login quando o backend responde 401', async () => {
    let status = 0;
    http.get('/api/orders').subscribe({ error: (e) => (status = e.status) });
    await flush();

    controller.expectOne('/api/orders').flush('expirado', { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(auth.login).toHaveBeenCalledTimes(1);
  });

  it('nao forca login em 403 (sem permissao nao e sessao expirada)', async () => {
    http.get('/api/admin/summary').subscribe({ error: () => undefined });
    await flush();

    controller.expectOne('/api/admin/summary').flush('', { status: 403, statusText: 'Forbidden' });

    expect(auth.login).not.toHaveBeenCalled();
  });
});
