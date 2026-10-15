import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from '../models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.usuario() ? true : inject(Router).parseUrl('/login');
};

export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.usuario() && !auth.saliendo ? inject(Router).parseUrl(auth.inicio()) : true;
};

export const rolGuard = (rol: Rol): CanActivateFn => () => {
  const auth = inject(AuthService);
  return auth.rol() === rol ? true : inject(Router).parseUrl(auth.inicio());
};
