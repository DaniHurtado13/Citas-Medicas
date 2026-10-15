import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../ui.service';

/**
 * Un 401 con la sesión abierta significa que el token venció (60 min) o que el back lo revocó
 * (cambio de contraseña, doctor desactivado). Se cierra la sesión en un solo lugar para toda la app.
 * Sin sesión abierta (p. ej. contraseña incorrecta en el login), el error sigue su camino sin más.
 */
export const sesionExpiradaInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && auth.usuario() && !auth.saliendo) {
        auth.salir().then(() => toast.show('Tu sesión terminó. Vuelve a iniciar sesión.', 'alert'));
      }
      return throwError(() => err);
    }),
  );
};
