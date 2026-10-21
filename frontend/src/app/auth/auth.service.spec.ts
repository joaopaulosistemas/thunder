import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    service = TestBed.inject(AuthService);
  });

  it('isAdmin so e verdadeiro quando a role admin esta no token', () => {
    service.roles.set(['user']);
    expect(service.isAdmin()).toBeFalse();

    service.roles.set(['user', 'admin']);
    expect(service.isAdmin()).toBeTrue();
  });

  it('nao entrega token antes de inicializar (nunca devolve string vazia silenciosamente)', async () => {
    await expectAsync(service.token()).toBeRejectedWithError(/nao inicializado/);
  });
});
