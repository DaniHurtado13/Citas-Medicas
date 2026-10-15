import { Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { DoctoresApi } from '../../core/api/doctores.api';
import { DoctorResponse } from '../../core/api/dtos';
import { AuthService } from '../../core/auth/auth.service';
import { mensajeDeError } from '../../core/http/errores';
import { ToastService } from '../../core/ui.service';
import { Rol } from '../../core/models';
import { errorPassword, PASSWORD_MAX } from '../../core/validacion';
import { Avatar } from '../../shared/ui';

const TIPO_CUENTA: Record<Rol, string> = { DOCTOR: 'Doctor', ADMIN: 'Administrador', PACIENTE: 'Paciente' };

/**
 * Perfil de solo lectura (decisión D2): muestra lo que el back sabe de la cuenta y permite cambiar la contraseña.
 * El back no tiene endpoint para editar los datos personales; los del doctor los cambia el admin.
 */
@Component({
  selector: 'app-perfil',
  imports: [Avatar],
  templateUrl: './perfil.html',
})
export class Perfil {
  protected readonly auth = inject(AuthService);
  private readonly authApi = inject(AuthApi);
  private readonly doctoresApi = inject(DoctoresApi);
  private readonly toast = inject(ToastService);

  protected readonly rol = computed(() => this.auth.rol()!);
  protected readonly tipoCuenta = computed(() => TIPO_CUENTA[this.rol()]);
  protected readonly passwordMax = PASSWORD_MAX;

  /** Ficha del doctor (especialidad, consultorio, duración): /yo solo trae su id */
  protected readonly doctor = signal<DoctorResponse | null>(null);

  protected readonly sub = computed(() => {
    const d = this.doctor();
    return d ? `${d.especialidadNombre}, ${d.consultorio}` : this.tipoCuenta();
  });

  // Cambio de contraseña
  protected readonly actual = signal('');
  protected readonly nueva = signal('');
  protected readonly confirmar = signal('');
  protected readonly campo = signal<'actual' | 'nueva' | 'confirmar' | null>(null);
  protected readonly error = signal('');
  protected readonly guardando = signal(false);

  constructor() {
    const doctorId = this.auth.usuario()?.doctorId;
    if (doctorId) firstValueFrom(this.doctoresApi.obtener(doctorId)).then((d) => this.doctor.set(d)).catch(() => {});
  }

  protected async cambiarPassword(e: Event) {
    e.preventDefault();
    if (!this.actual()) return this.falla('actual', 'Escribe tu contraseña actual.');
    const problema = errorPassword(this.nueva());
    if (problema) return this.falla('nueva', problema);
    if (this.nueva() === this.actual()) return this.falla('nueva', 'La nueva debe ser distinta de la actual.');
    if (this.confirmar() !== this.nueva()) return this.falla('confirmar', 'Las contraseñas nuevas no coinciden.');
    this.falla(null, '');

    this.guardando.set(true);
    try {
      await firstValueFrom(this.authApi.cambiarPassword({ contrasenaActual: this.actual(), nuevaContrasena: this.nueva() }));
      // El back cerró todas las sesiones, también esta: hay que volver a entrar
      await this.auth.salir();
      this.toast.show('Contraseña actualizada. Inicia sesión con la nueva.');
    } catch (err) {
      // 400 (no 401): contraseña actual incorrecta, así el interceptor no cierra la sesión por un error de tipeo
      this.falla('actual', mensajeDeError(err));
    } finally {
      this.guardando.set(false);
    }
  }

  private falla(campo: 'actual' | 'nueva' | 'confirmar' | null, msg: string) {
    this.campo.set(campo);
    this.error.set(msg);
  }
}
