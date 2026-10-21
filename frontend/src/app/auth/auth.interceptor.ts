import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Anexa "Authorization: Bearer <token>" em toda chamada para /api.
 * O token e pedido ao AuthService a cada requisicao, que o renova se preciso.
 * Se o backend responder 401 (sessao invalida), volta para o login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) {
    return next(req);
  }
  const auth = inject(AuthService);
  return from(auth.token()).pipe(
    switchMap((token) => next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.login();
      }
      return throwError(() => error);
    }),
  );
};
