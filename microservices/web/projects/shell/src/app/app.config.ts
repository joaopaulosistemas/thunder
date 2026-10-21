import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './auth/auth.service';
import { authInterceptor } from './auth/auth.interceptor';

// O shell e o dono da identidade: faz o login e o interceptor de token. Os microfrontends rodam
// dentro desta aplicacao e reaproveitam o mesmo HttpClient (@angular/common/http e compartilhado),
// entao todas as chamadas deles tambem saem com "Authorization: Bearer".
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAppInitializer(() => inject(AuthService).init()),
    provideRouter(routes),
  ],
};
