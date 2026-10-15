import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { AuthService } from '../../core/auth/auth.service';
import { mensajeDeError } from '../../core/http/errores';
import { BootService } from '../../core/ui.service';
import { VistaStateService } from '../../core/vista-state.service';
import { Rol } from '../../core/models';
import { wait } from '../../core/fechas';
import { Icon } from '../../shared/icons';
import { CUENTAS_DEMO } from './cuentas-demo';
import { EMAIL_RE } from '../../core/validacion';

const BOOT_TEXT: Record<Rol, string> = {
  DOCTOR: 'Preparando tu agenda…',
  ADMIN: 'Cargando el panel de administración…',
  PACIENTE: 'Buscando tus citas…',
};


@Component({
  selector: 'app-login',
  imports: [Icon, RouterLink],
  templateUrl: './login.html',
  host: { style: 'display: contents' }, // el marco lo pone layout/acceso.ts
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly boot = inject(BootService);
  private readonly vista = inject(VistaStateService);

  protected readonly cuentasDemo = CUENTAS_DEMO;
  protected readonly email = signal('');
  protected readonly pass = signal('');
  protected readonly showPass = signal(false);
  protected readonly error = signal('');
  protected readonly errField = signal<'email' | 'pass' | null>(null);
  protected readonly loading = signal(false);
  /** 403 del back: la contraseña es correcta, pero el correo aún no se confirmó */
  protected readonly sinConfirmar = signal(false);
  protected readonly reenviando = signal(false);
  protected readonly reenviado = signal(false);

  /** Rol de la cuenta de ejemplo que está entrando (un solo intento a la vez) */
  protected readonly entrandoComo = signal<Rol | null>(null);
  protected readonly demoError = signal('');
  protected readonly ocupado = computed(() => this.loading() || this.entrandoComo() !== null);

  protected async submit(e: Event) {
    e.preventDefault();
    this.errField.set(null);
    const email = this.email().trim();
    if (!EMAIL_RE.test(email)) {
      this.error.set('Escribe un correo válido, por ejemplo nombre@correo.co.');
      this.errField.set('email');
      return;
    }
    if (!this.pass()) {
      this.error.set('Escribe tu contraseña.');
      this.errField.set('pass');
      return;
    }
    this.error.set('');
    this.demoError.set('');
    this.sinConfirmar.set(false);
    this.reenviado.set(false);
    this.loading.set(true);

    try {
      await this.auth.login(email, this.pass());
    } catch (err) {
      this.sinConfirmar.set(err instanceof HttpErrorResponse && err.status === 403);
      this.error.set(mensajeDeError(err, {
        401: 'El correo o la contraseña no coinciden. Revísalos e intenta de nuevo.',
        403: 'Aún no confirmas tu correo. Abre el enlace que te enviamos para activar tu cuenta.',
      }));
      return;
    } finally {
      this.loading.set(false);
    }

    this.pass.set('');
    await this.entrarALaApp();
  }

  protected async entrarComoDemo(rol: Rol) {
    this.error.set('');
    this.demoError.set('');
    this.entrandoComo.set(rol);
    try {
      await this.auth.entrarComoDemo(rol);
    } catch (err) {
      this.demoError.set(mensajeDeError(err, {
        404: 'Esta cuenta de ejemplo no está disponible ahora. Prueba con otra o vuelve más tarde.',
      }));
      return;
    } finally {
      this.entrandoComo.set(null);
    }
    await this.entrarALaApp();
  }

  /** El back responde 202 exista o no la cuenta, así que el mensaje es el mismo siempre */
  protected async reenviar() {
    this.reenviando.set(true);
    try {
      await firstValueFrom(this.authApi.reenviarVerificacion(this.email().trim()));
      this.reenviado.set(true);
    } catch (err) {
      this.error.set(mensajeDeError(err));
    } finally {
      this.reenviando.set(false);
    }
  }

  private async entrarALaApp() {
    this.boot.show(BOOT_TEXT[this.auth.rol()!]);
    await wait(1100);
    this.vista.reset();
    await this.router.navigateByUrl(this.auth.inicio());
    await this.boot.hide();
  }
}
