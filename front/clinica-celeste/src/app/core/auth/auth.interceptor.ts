import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStorage } from './token.storage';

/** Agrega el JWT a las peticiones de la API. Nunca lo manda a otros dominios (fuentes, CDNs…) */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStorage).leer();
  if (!token || !req.url.startsWith('/api/')) return next(req);
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
