import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { AuthApi } from '../api/auth.api';
import { LoginResponse, UsuarioActualResponse } from '../api/dtos';
import { Rol } from '../models';
import { tokenVencido, TokenStorage } from './token.storage';

export const INICIO: Record<Rol, string> = {
  DOCTOR: '/doctor/resumen',
  ADMIN: '/admin/resumen',
  PACIENTE: '/paciente/inicio',
};

const ROL_TEXTO: Record<Rol, string> = { DOCTOR: 'Doctor', ADMIN: 'Administrador', PACIENTE: 'Paciente' };

/**
 * Sesión real: el token sale de POST /api/auth/login y los datos del usuario de GET /api/auth/yo.
 * El rol lo decide el back (va dentro del token); el front solo lo lee.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly tokens = inject(TokenStorage);
  private readonly router = inject(Router);

  private readonly yo = signal<UsuarioActualResponse | null>(null);

  readonly usuario = this.yo.asReadonly();
  readonly rol = computed(() => this.yo()?.rol ?? null);

  /** Nombre para mostrar: el admin no tiene nombre en el backend, así que se usa su correo */
  readonly nombre = computed(() => this.yo()?.nombre ?? this.yo()?.email ?? '');

  readonly subtitulo = computed(() => {
    const rol = this.rol();
    return rol ? ROL_TEXTO[rol] : '';
  });

  readonly inicio = computed(() => INICIO[this.rol() ?? 'PACIENTE']);

  /** true mientras se navega al login al cerrar sesión */
  saliendo = false;

  async login(email: string, password: string): Promise<void> {
    await this.iniciar(this.api.login({ email: email.trim(), password }));
  }

  async entrarComoDemo(rol: Rol): Promise<void> {
    await this.iniciar(this.api.demo({ rol }));
  }

  /** Pide el token, lo guarda y carga al usuario. Si algo falla, lanza el HttpErrorResponse para que la vista lo muestre */
  private async iniciar(peticion: Observable<LoginResponse>): Promise<void> {
    const { token } = await firstValueFrom(peticion);
    this.tokens.guardar(token);
    try {
      this.yo.set(await firstValueFrom(this.api.yo()));
    } catch (e) {
      this.tokens.borrar();
      throw e;
    }
  }

  /** Al recargar la página: si hay un token vigente, recupera al usuario sin pedir la contraseña otra vez */
  async restaurar(): Promise<void> {
    const token = this.tokens.leer();
    if (!token) return;
    if (tokenVencido(token)) {
      this.tokens.borrar();
      return;
    }
    try {
      this.yo.set(await firstValueFrom(this.api.yo()));
    } catch (e) {
      // 401 o 403: el token ya no sirve. Otro error (p. ej. el back apagado): se conserva para el próximo intento
      if (e instanceof HttpErrorResponse && (e.status === 401 || e.status === 403)) this.tokens.borrar();
    }
  }

  /** Primero se sale de la app y luego se borra la sesión, así ninguna vista se pinta sin usuario */
  async salir(): Promise<void> {
    this.saliendo = true;
    await this.router.navigateByUrl('/login');
    this.tokens.borrar();
    this.yo.set(null);
    this.saliendo = false;
  }
}
