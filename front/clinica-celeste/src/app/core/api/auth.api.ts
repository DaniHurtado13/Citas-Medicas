import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  CambiarPasswordRequest, DemoLoginRequest, LoginRequest, LoginResponse, PacienteResponse, RegistroPacienteRequest, UsuarioActualResponse,
} from './dtos';

const URL = '/api/auth';

/** Endpoints de /api/auth. Solo hace las peticiones: no guarda estado ni decide qué hacer con los errores */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  login(body: LoginRequest) {
    return this.http.post<LoginResponse>(`${URL}/login`, body);
  }

  /** Cuentas de ejemplo: el back elige la cuenta del rol y no pide contraseña. 404 si no hay demo en ese servidor */
  demo(body: DemoLoginRequest) {
    return this.http.post<LoginResponse>(`${URL}/demo`, body);
  }

  yo() {
    return this.http.get<UsuarioActualResponse>(`${URL}/yo`);
  }

  // ---------- Cuenta (públicos) ----------
  /** Crea al paciente sin verificar y le envía el correo de confirmación */
  registro(body: RegistroPacienteRequest) {
    return this.http.post<PacienteResponse>(`${URL}/registro`, body);
  }

  /** El token llega en el enlace del correo; se usa POST para que los antivirus que abren enlaces no lo gasten */
  verificarEmail(token: string) {
    return this.http.post<void>(`${URL}/verificar-email`, { token });
  }

  /** Responde 202 siempre, exista o no la cuenta: no revela qué correos están registrados */
  reenviarVerificacion(email: string) {
    return this.http.post<void>(`${URL}/reenviar-verificacion`, { email });
  }

  /** Responde 202 siempre, exista o no la cuenta */
  olvidePassword(email: string) {
    return this.http.post<void>(`${URL}/olvide-password`, { email });
  }

  restablecerPassword(token: string, nuevaContrasena: string) {
    return this.http.post<void>(`${URL}/restablecer-password`, { token, nuevaContrasena });
  }

  // ---------- Con sesión ----------
  /** 204: cambia la contraseña y cierra TODAS las sesiones, también la actual */
  cambiarPassword(body: CambiarPasswordRequest) {
    return this.http.patch<void>(`${URL}/password`, body);
  }
}
