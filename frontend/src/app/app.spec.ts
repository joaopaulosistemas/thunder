import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from './api.service';
import { App } from './app';
import { AuthService } from './auth/auth.service';

function setup(roles: string[]) {
  const api = jasmine.createSpyObj<ApiService>('ApiService', [
    'products',
    'orders',
    'notifications',
    'me',
    'adminSummary',
    'createOrder',
  ]);
  api.products.and.returnValue(of([{ id: 1, sku: 'BK-001', name: 'Livro', priceCents: 8990 }]));
  api.orders.and.returnValue(of([]));
  api.notifications.and.returnValue(of([]));
  api.me.and.returnValue(of({ username: 'alice', roles }));
  api.adminSummary.and.returnValue(of({ orders: 3, revenueCents: 12345 }));
  api.createOrder.and.returnValue(
    of({ id: 9, productId: 1, quantity: 1, totalCents: 8990, customer: 'alice', createdAt: '2026-10-18T00:00:00Z' }),
  );

  TestBed.configureTestingModule({
    imports: [App],
    providers: [{ provide: ApiService, useValue: api }],
  });
  const auth = TestBed.inject(AuthService);
  auth.username.set('alice');
  auth.roles.set(roles);

  const fixture = TestBed.createComponent(App);
  fixture.detectChanges();
  return { api, fixture, text: () => (fixture.nativeElement as HTMLElement).textContent ?? '' };
}

describe('App', () => {
  it('usuario comum: ve so os proprios pedidos e nao busca o resumo admin', () => {
    const { api, text } = setup(['user']);

    expect(text()).toContain('alice');
    expect(text()).toContain('Meus ultimos pedidos');
    expect(text()).not.toContain('Painel admin');
    expect(api.adminSummary).not.toHaveBeenCalled();
  });

  it('admin: ve o painel admin com o resumo e a lista de todos os pedidos', () => {
    const { api, text } = setup(['user', 'admin']);

    expect(text()).toContain('Painel admin');
    expect(text()).toContain('3 pedidos');
    expect(text()).toContain('Ultimos pedidos (todos)');
    expect(api.adminSummary).toHaveBeenCalled();
  });

  it('mostra o que o backend enxergou no token', () => {
    const { text } = setup(['user']);

    expect(text()).toContain('Backend enxergou');
  });

  it('enviar o formulario cria o pedido pelo produto escolhido e confirma na tela', () => {
    const { api, fixture, text } = setup(['user']);

    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(api.createOrder).toHaveBeenCalledWith(1, 1);
    expect(text()).toContain('Pedido #9 criado.');
  });
});
