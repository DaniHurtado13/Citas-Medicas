import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { sesionExpiradaInterceptor } from './core/http/sesion-expirada.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, sesionExpiradaInterceptor])),
    // Antes de la primera navegación: si quedó un token de esta pestaña, se recupera la sesión
    provideAppInitializer(() => inject(AuthService).restaurar()),
  ]
};
